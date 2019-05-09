package org.tron.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.StringUtil;
import org.tron.core.Wallet;
import org.tron.core.capsule.*;
import org.tron.core.config.Parameter;
import org.tron.core.config.args.Args;
import org.tron.core.db.Manager;
import org.tron.core.db.StakeChangeStore;
import org.tron.core.db.StakeAccountStore;
import org.tron.core.exception.ContractExeException;
import org.tron.core.exception.ContractValidateException;
import org.tron.protos.Contract;
import org.tron.protos.Protocol.Transaction.Result.code;

import java.util.Objects;

@Slf4j(topic = "actuator")
public class StakeActuator extends AbstractActuator {

	StakeActuator(Any contract, Manager dbManager) {
		super(contract, dbManager);
	}

	@Override
	public boolean execute(TransactionResultCapsule ret) throws ContractExeException {
		long fee = calcFee();
		final Contract.StakeContract stakeContract;
		try {
			stakeContract = contract.unpack(Contract.StakeContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			ret.setStatus(fee, code.FAILED);
			throw new ContractExeException(e.getMessage());
		}
		AccountCapsule accountCapsule = dbManager.getAccountStore()
				.get(stakeContract.getOwnerAddress().toByteArray());

		long now = dbManager.getHeadBlockTimeStamp();
		long duration = stakeContract.getStakeDuration() * 86_400_000;

		long newBalance = accountCapsule.getBalance() - stakeContract.getStakeAmount();

		long stakeAmount = stakeContract.getStakeAmount();
		long expireTime = now + duration;
		byte[] ownerAddress = stakeContract.getOwnerAddress().toByteArray();

		long newStakeAmount = stakeAmount + accountCapsule.getStakeAmount();
		accountCapsule.setStake(newStakeAmount, expireTime);
//		TODO: set total stake amount
//		dbManager.getDynamicPropertiesStore()
//				.addTotalNetWeight(stakeAmount / 1000_000L);

		accountCapsule.setBalance(newBalance);
		dbManager.getAccountStore().put(accountCapsule.createDbKey(), accountCapsule);

		this.recalculateStake(ownerAddress);

		ret.setStatus(fee, code.SUCCESS);

		return true;
	}


	@Override
	public boolean validate() throws ContractValidateException {
		if (this.contract == null) {
			throw new ContractValidateException("No contract!");
		}
		if (this.dbManager == null) {
			throw new ContractValidateException("No dbManager!");
		}
		if (!contract.is(Contract.StakeContract.class)) {
			throw new ContractValidateException(
					"contract type error,expected type StakeContract, real type " + contract.getClass());
		}

		final Contract.StakeContract stakeContract;
		try {
			stakeContract = this.contract.unpack(Contract.StakeContract.class);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
			throw new ContractValidateException(e.getMessage());
		}
		byte[] ownerAddress = stakeContract.getOwnerAddress().toByteArray();
		if (!Wallet.addressValid(ownerAddress)) {
			throw new ContractValidateException("Invalid address");
		}

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(ownerAddress);
		if (accountCapsule == null) {
			String readableOwnerAddress = StringUtil.createReadableString(ownerAddress);
			throw new ContractValidateException(
					"Account [" + readableOwnerAddress + "] not exists");
		}
		long stakeAmount = stakeContract.getStakeAmount();

		if (stakeAmount <= 0) {
			throw new ContractValidateException("Stake amount must be positive");
		}
		if (stakeAmount < 1_000_000L) {
			throw new ContractValidateException("Stake amount must be more than 1 MCASH");
		}

		int stakesCount = accountCapsule.getStakesCount();
		if (!(stakesCount == 0 || stakesCount == 1)) {
			throw new ContractValidateException("stakesCount must be 0 or 1");
		}
		if (stakeAmount > accountCapsule.getBalance()) {
			throw new ContractValidateException("Stake amount must be less than accountBalance");
		}

		long stakeDuration = stakeContract.getStakeDuration();
		long stakeTime = Parameter.ChainConstant.STAKE_TIME_IN_DAY;

		boolean needCheckTime = Args.getInstance().getCheckStakeTime() == 1;//for test
		if (needCheckTime && stakeDuration != stakeTime) {
			throw new ContractValidateException(
					"Stake duration must be " + stakeTime + " days");
		}

		return true;
	}

	@Override
	public ByteString getOwnerAddress() throws InvalidProtocolBufferException {
		return contract.unpack(Contract.StakeContract.class).getOwnerAddress();
	}

	@Override
	public long calcFee() {
		return 0;
	}

	private void recalculateStake(byte[] address) {
		StakeChangeStore stakeChangeStore = dbManager.getStakeChangeStore();
		StakeAccountStore stakeAccountStore = dbManager.getStakeAccountStore();

		AccountCapsule accountCapsule = dbManager.getAccountStore().get(address);
		if (accountCapsule == null) {
			return;
		}

		long stakeAmount = accountCapsule.getStakeAmount();
		if (stakeAmount <= 0) {
			return;
		}

		StakeAccountCapsule stakeAccountCapsule = stakeAccountStore.get(address);
		StakeChangeCapsule stakeChangeCapsule;

		if (!Objects.isNull(getDeposit())) {
			StakeChangeCapsule sCapsule = getDeposit().getStakeChangeCapsule(address);
			if (Objects.isNull(sCapsule)) {
				stakeChangeCapsule = new StakeChangeCapsule(ByteString.copyFrom(address),
						accountCapsule.getStakeAmount());
			} else {
				stakeChangeCapsule = sCapsule;
			}
		} else if (!stakeChangeStore.has(address)) {
			if (stakeAccountCapsule != null)
				stakeChangeCapsule = new StakeChangeCapsule(ByteString.copyFrom(address),
						stakeAccountCapsule.getStakeAmount());
			else
				stakeChangeCapsule = new StakeChangeCapsule(ByteString.copyFrom(address));
		} else {
			stakeChangeCapsule = stakeChangeStore.get(address);
		}

		stakeChangeCapsule.setNewStakeAmount(stakeAmount);

		if (Objects.isNull(deposit)) {
			stakeChangeStore.put(address, stakeChangeCapsule);
		} else {
			// cache
			deposit.putStakeChangeValue(address, stakeChangeCapsule);
		}

	}

}
