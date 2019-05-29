/*
 * java-tron is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * java-tron is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package io.midasprotocol.core.actuator;

import com.google.protobuf.Any;
import com.google.protobuf.ByteString;
import io.midasprotocol.core.Wallet;
import lombok.extern.slf4j.Slf4j;
import org.junit.*;
import io.midasprotocol.common.application.ApplicationContext;
import io.midasprotocol.common.utils.ByteArray;
import io.midasprotocol.common.utils.FileUtil;
import io.midasprotocol.core.Constant;
import io.midasprotocol.core.capsule.AccountCapsule;
import io.midasprotocol.core.capsule.AssetIssueCapsule;
import io.midasprotocol.core.capsule.TransactionResultCapsule;
import io.midasprotocol.core.config.DefaultConfig;
import io.midasprotocol.core.config.args.Args;
import io.midasprotocol.core.db.Manager;
import io.midasprotocol.core.exception.ContractExeException;
import io.midasprotocol.core.exception.ContractValidateException;
import io.midasprotocol.protos.Contract;
import io.midasprotocol.protos.Contract.AssetIssueContract;
import io.midasprotocol.protos.Protocol.AccountType;
import io.midasprotocol.protos.Protocol.Transaction.Result.code;

import java.io.File;

import static junit.framework.TestCase.fail;

@Slf4j
public class TransferAssetActuatorTest {

	private static final String dbPath = "output_transfer_asset_test";
	private static final String ASSET_NAME = "mcash";
	private static final String OWNER_ADDRESS;
	private static final String TO_ADDRESS;
	private static final String NOT_EXIT_ADDRESS;
	private static final String NOT_EXIT_ADDRESS_2;
	private static final long OWNER_ASSET_BALANCE = 99999;
	private static final String ownerAsset_ADDRESS;
	private static final String ownerASSET_NAME = "mcashtest";
	private static final long OWNER_ASSET_Test_BALANCE = 99999;
	private static final String OWNER_ADDRESS_INVALID = "cccc";
	private static final String TO_ADDRESS_INVALID = "dddd";
	private static final long TOTAL_SUPPLY = 10L;
	private static final int TRX_NUM = 10;
	private static final int NUM = 1;
	private static final long START_TIME = 1;
	private static final long END_TIME = 2;
	private static final int VOTE_SCORE = 2;
	private static final String DESCRIPTION = "MCASH";
	private static final String URL = "https://mcash.network";
	private static ApplicationContext context;
	private static Manager dbManager;
	private static Any contract;

	static {
		Args.setParam(new String[]{"--output-directory", dbPath}, Constant.TEST_CONF);
		context = new ApplicationContext(DefaultConfig.class);
		OWNER_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049150";
		TO_ADDRESS = Wallet.getAddressPreFixString() + "548794500882809695a8a687866e76d4271a146a";
		NOT_EXIT_ADDRESS = Wallet.getAddressPreFixString() + "B56446E617E924805E4D6CA021D341FEF6E2013B";
		NOT_EXIT_ADDRESS_2 = Wallet.getAddressPreFixString() + "B56446E617E924805E4D6CA021D341FEF6E21234";
		ownerAsset_ADDRESS = Wallet.getAddressPreFixString() + "abd4b9367799eaa3197fecb144eb71de1e049010";
	}

	/**
	 * Init data.
	 */
	@BeforeClass
	public static void init() {
		dbManager = context.getBean(Manager.class);
	}

	/**
	 * Release resources.
	 */
	@AfterClass
	public static void destroy() {
		Args.clearParam();
		context.destroy();
		if (FileUtil.deleteDir(new File(dbPath))) {
			logger.info("Release resources successful.");
		} else {
			logger.info("Release resources failure.");
		}
	}

	/**
	 * create temp Capsule test need.
	 */
	@Before
	public void createCapsule() {
		AccountCapsule toAccountCapsule =
				new AccountCapsule(
						ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)),
						ByteString.copyFromUtf8("toAccount"),
						AccountType.Normal);
		dbManager.getAccountStore().put(toAccountCapsule.getAddress().toByteArray(), toAccountCapsule);
	}

	private boolean isNullOrZero(Long value) {
		if (null == value || value == 0) {
			return true;
		}
		return false;
	}

	public void createAsset(String assetName) {
		AccountCapsule ownerCapsule = dbManager.getAccountStore()
				.get(ByteArray.fromHexString(OWNER_ADDRESS));

		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		ownerCapsule.addAssetV2(id, OWNER_ASSET_BALANCE);
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
		AssetIssueContract assetIssueContract =
				AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFrom(ByteArray.fromString(assetName)))
						.setId(id)
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(START_TIME)
						.setEndTime(END_TIME)
						.setVoteScore(VOTE_SCORE)
						.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
						.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
						.build();
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
		dbManager.getAssetIssueStore()
				.put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
	}

	private Any getContract(long sendCoin) {
		long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		return Any.pack(
				Contract.TransferAssetContract.newBuilder()
						.setAssetId(tokenIdNum)
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
						.setAmount(sendCoin)
						.build());
	}

	private Any getContract(long sendCoin, long assetId) {
		return Any.pack(
				Contract.TransferAssetContract.newBuilder()
						.setAssetId(assetId)
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(TO_ADDRESS)))
						.setAmount(sendCoin)
						.build());
	}

	private Any getContract(long sendCoin, String owner, String to) {
		long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		return Any.pack(
				Contract.TransferAssetContract.newBuilder()
						.setAssetId(tokenIdNum)
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(owner)))
						.setToAddress(ByteString.copyFrom(ByteArray.fromHexString(to)))
						.setAmount(sendCoin)
						.build());
	}

	private void createAssertSameTokenNameActive() {
		long id = dbManager.getDynamicPropertiesStore().getTokenIdNum() + 1;
		dbManager.getDynamicPropertiesStore().saveTokenIdNum(id);
		AssetIssueContract assetIssueContract =
				AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)))
						.setName(ByteString.copyFrom(ByteArray.fromString(ASSET_NAME)))
						.setId(id)
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setNum(NUM)
						.setStartTime(START_TIME)
						.setEndTime(END_TIME)
						.setVoteScore(VOTE_SCORE)
						.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
						.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
						.build();
		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueContract);
		dbManager.getAssetIssueStore().put(assetIssueCapsule.createDbKey(), assetIssueCapsule);

		AccountCapsule ownerCapsule =
				new AccountCapsule(
						ByteString.copyFrom(ByteArray.fromHexString(OWNER_ADDRESS)),
						ByteString.copyFromUtf8("owner"),
						AccountType.AssetIssue);

		ownerCapsule.addAssetV2(id, OWNER_ASSET_BALANCE);
		dbManager.getAccountStore().put(ownerCapsule.getAddress().toByteArray(), ownerCapsule);
	}

	/**
	 * SameTokenName open, transfer assert success.
	 */
	@Test
	public void SameTokenNameOpenSuccessTransfer() {
		createAssertSameTokenNameActive();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(100L), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
					dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(
					owner.getInstance().getAssetMap().get(tokenIdNum).longValue(),
					OWNER_ASSET_BALANCE - 100);
			Assert.assertEquals(
					toAccount.getInstance().getAssetMap().get(tokenIdNum).longValue(),
					100L);
		} catch (ContractValidateException e) {
			Assert.assertFalse(e instanceof ContractValidateException);
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open, transfer assert success.
	 */
	@Test
	public void SameTokenNameOpenSuccessTransfer2() {
		createAssertSameTokenNameActive();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
					dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(
					owner.getInstance().getAssetMap().get(tokenIdNum).longValue(), 0L);
			Assert.assertEquals(
					toAccount.getInstance().getAssetMap().get(tokenIdNum).longValue(),
					OWNER_ASSET_BALANCE);
		} catch (ContractValidateException e) {
			Assert.assertFalse(e instanceof ContractValidateException);
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open,no Assert.
	 */
	@Test
	public void SameTokenNameOpenOwnerNoAssetTest() {
		createAssertSameTokenNameActive();
		AccountCapsule owner = dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
		owner.setInstance(owner.getInstance().toBuilder().clearAsset().build());
		dbManager.getAccountStore().put(owner.createDbKey(), owner);
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("Owner no asset!", e.getMessage());
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule toAccount =
					dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open,Unit test.
	 */
	@Test
	public void SameTokenNameOpenNotEnoughAssetTest() {
		createAssertSameTokenNameActive();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(OWNER_ASSET_BALANCE + 1),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertTrue("assetBalance is not sufficient.".equals(e.getMessage()));
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
					dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMapV2().get(tokenIdNum).longValue(),
					OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open, zero amount
	 */
	@Test
	public void SameTokenNameOpenZeroAmountTest() {
		createAssertSameTokenNameActive();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(0), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertTrue("Amount must greater than 0.".equals(e.getMessage()));
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
					dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMapV2().get(tokenIdNum).longValue(),
					OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open, negative amount
	 */
	@Test
	public void SameTokenNameOpenNegativeAmountTest() {
		createAssertSameTokenNameActive();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(-999), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("Amount must greater than 0.", e.getMessage());
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
					dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMapV2().get(tokenIdNum).longValue(),
					OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open, no exist assert
	 */
	@Test
	public void SameTokenNameOpenNoneExistAssetTest() {
		createAssertSameTokenNameActive();
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(1, 10000000L),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertTrue("No asset !".equals(e.getMessage()));
			Assert.assertEquals(ret.getInstance().getRet(), code.SUCCESS);
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
					dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMapV2().get(tokenIdNum).longValue(),
					OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open,If to account not exit, create it.
	 */
	@Test
	public void SameTokenNameOpenNoExitToAccount() {
		createAssertSameTokenNameActive();
		TransferAssetActuator actuator = new TransferAssetActuator(
				getContract(100L, OWNER_ADDRESS, NOT_EXIT_ADDRESS_2), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			AccountCapsule noExitAccount = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(NOT_EXIT_ADDRESS_2));
			Assert.assertNull(noExitAccount);
			actuator.validate();
			actuator.execute(ret);
			noExitAccount = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(NOT_EXIT_ADDRESS_2));
			Assert.assertNotNull(noExitAccount);    //Had created.
			Assert.assertEquals(noExitAccount.getBalance(), 0);
			actuator.execute(ret);
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert
					.assertEquals("Validate TransferAssetActuator error, insufficient fee.", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(OWNER_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMapV2().get(tokenIdNum).longValue(),
					OWNER_ASSET_BALANCE);
			AccountCapsule noExitAccount = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(NOT_EXIT_ADDRESS_2));
			Assert.assertTrue(noExitAccount == null);
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open, add over flow
	 */
	@Test
	public void SameTokenNameOpenAddOverflowTest() {
		createAssertSameTokenNameActive();
		// First, increase the to balance. Else can't complete this test case.
		AccountCapsule toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
		long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
		toAccount.addAssetV2(tokenIdNum, Long.MAX_VALUE);
		dbManager.getAccountStore().put(ByteArray.fromHexString(TO_ADDRESS), toAccount);
		TransferAssetActuator actuator = new TransferAssetActuator(getContract(1), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("long overflow", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			toAccount = dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));

			Assert.assertEquals(owner.getAssetMapV2().get(tokenIdNum).longValue(),
					OWNER_ASSET_BALANCE);
			Assert.assertEquals(toAccount.getAssetMapV2().get(tokenIdNum).longValue(),
					Long.MAX_VALUE);
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open,transfer asset to yourself,result is error
	 */
	@Test
	public void SameTokenNameOpenTransferToYourself() {
		createAssertSameTokenNameActive();
		TransferAssetActuator actuator = new TransferAssetActuator(
				getContract(100L, OWNER_ADDRESS, OWNER_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			fail("Cannot transfer asset to yourself.");

		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("Cannot transfer asset to yourself.", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMapV2().get(tokenIdNum).longValue(),
					OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open,Invalid ownerAddress,result is error
	 */
	@Test
	public void SameTokenNameOpenInvalidOwnerAddress() {
		createAssertSameTokenNameActive();
		TransferAssetActuator actuator = new TransferAssetActuator(
				getContract(100L, OWNER_ADDRESS_INVALID, TO_ADDRESS), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			fail("Invalid ownerAddress");
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("Invalid ownerAddress", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMapV2().get(tokenIdNum).longValue(),
					OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.assertTrue(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open,Invalid ToAddress,result is error
	 */
	@Test
	public void SameTokenNameOpenInvalidToAddress() {
		createAssertSameTokenNameActive();
		TransferAssetActuator actuator = new TransferAssetActuator(
				getContract(100L, OWNER_ADDRESS, TO_ADDRESS_INVALID), dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			fail("Invalid toAddress");
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("Invalid toAddress", e.getMessage());
			AccountCapsule owner = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount = dbManager.getAccountStore()
					.get(ByteArray.fromHexString(TO_ADDRESS));
			long tokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMapV2().get(tokenIdNum).longValue(),
					OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(tokenIdNum)));
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

	/**
	 * SameTokenName open,Account do not have this asset,Transfer this Asset result is failed
	 */
	@Test
	public void SameTokenNameOpenOwnerNoThisAsset() {
		createAssertSameTokenNameActive();
		long tokenIdNum = 2000000;
		AccountCapsule ownerAssetCapsule =
				new AccountCapsule(
						ByteString.copyFrom(ByteArray.fromHexString(ownerAsset_ADDRESS)),
						ByteString.copyFromUtf8("ownerAsset"),
						AccountType.AssetIssue);
		ownerAssetCapsule.addAssetV2(tokenIdNum, OWNER_ASSET_Test_BALANCE);

		AssetIssueContract assetIssueTestContract =
				AssetIssueContract.newBuilder()
						.setOwnerAddress(ByteString.copyFrom(ByteArray.fromHexString(ownerAsset_ADDRESS)))
						.setName(ByteString.copyFrom(ByteArray.fromString(ownerASSET_NAME)))
						.setTotalSupply(TOTAL_SUPPLY)
						.setTrxNum(TRX_NUM)
						.setId(tokenIdNum)
						.setNum(NUM)
						.setStartTime(START_TIME)
						.setEndTime(END_TIME)
						.setVoteScore(VOTE_SCORE)
						.setDescription(ByteString.copyFrom(ByteArray.fromString(DESCRIPTION)))
						.setUrl(ByteString.copyFrom(ByteArray.fromString(URL)))
						.build();

		AssetIssueCapsule assetIssueCapsule = new AssetIssueCapsule(assetIssueTestContract);
		dbManager.getAccountStore()
				.put(ownerAssetCapsule.getAddress().toByteArray(), ownerAssetCapsule);
		dbManager.getAssetIssueStore()
				.put(assetIssueCapsule.createDbKey(), assetIssueCapsule);
		TransferAssetActuator actuator = new TransferAssetActuator(
				getContract(1, tokenIdNum),
				dbManager);
		TransactionResultCapsule ret = new TransactionResultCapsule();
		try {
			actuator.validate();
			actuator.execute(ret);
			Assert.fail();
		} catch (ContractValidateException e) {
			Assert.assertTrue(e instanceof ContractValidateException);
			Assert.assertEquals("assetBalance must greater than 0.", e.getMessage());
			AccountCapsule owner =
					dbManager.getAccountStore().get(ByteArray.fromHexString(OWNER_ADDRESS));
			AccountCapsule toAccount =
					dbManager.getAccountStore().get(ByteArray.fromHexString(TO_ADDRESS));
			long secondTokenIdNum = dbManager.getDynamicPropertiesStore().getTokenIdNum();
			Assert.assertEquals(owner.getAssetMapV2().get(secondTokenIdNum).longValue(),
					OWNER_ASSET_BALANCE);
			Assert.assertTrue(isNullOrZero(toAccount.getAssetMapV2().get(tokenIdNum)));
			AccountCapsule ownerAsset =
					dbManager.getAccountStore().get(ByteArray.fromHexString(ownerAsset_ADDRESS));
			Assert.assertEquals(ownerAsset.getAssetMapV2().get(tokenIdNum).longValue(),
					OWNER_ASSET_Test_BALANCE);
		} catch (ContractExeException e) {
			Assert.assertFalse(e instanceof ContractExeException);
		}
	}

}
