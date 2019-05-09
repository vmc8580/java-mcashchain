package org.tron.core.capsule;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import lombok.extern.slf4j.Slf4j;
import org.tron.common.utils.ByteArray;
import org.tron.protos.Protocol.StakeChange;

@Slf4j(topic = "capsule")
public class StakeChangeCapsule implements ProtoCapsule<StakeChange> {
	private StakeChange stakeChange;

	@Override
	public byte[] getData() {
		return this.stakeChange.toByteArray();
	}

	@Override
	public StakeChange getInstance() {
		return this.stakeChange;
	}

	/**
	 * StakeChangeCapsule constructor with address.
	 */
	public StakeChangeCapsule(final ByteString address) {
		this.stakeChange = StakeChange.newBuilder()
				.setAddress(address)
				.setOldStakeAmount(-1)
				.setNewStakeAmount(-1)
				.build();
	}

	public StakeChangeCapsule(final byte[] data) {
		try {
			this.stakeChange = StakeChange.parseFrom(data);
		} catch (InvalidProtocolBufferException e) {
			logger.debug(e.getMessage(), e);
		}
	}

	public StakeChangeCapsule(ByteString address, long stakeAmount) {
		StakeChange.Builder builder = StakeChange.newBuilder().setAddress(address);
		builder.setOldStakeAmount(stakeAmount);
		this.stakeChange = builder.build();
	}

	public byte[] createDbKey() {
		return getAddress().toByteArray();
	}

	public String createReadableString() {
		return ByteArray.toHexString(getAddress().toByteArray());
	}

	public ByteString getAddress() {
		return this.stakeChange.getAddress();
	}

	public long getNewStakeAmount() {
		return this.stakeChange.getNewStakeAmount();
	}

	public void setNewStakeAmount(long stakeAmount) {
		this.stakeChange = this.stakeChange.toBuilder()
				.setNewStakeAmount(stakeAmount)
				.build();
	}

	public long getOldStakeAmount() {
		return this.stakeChange.getOldStakeAmount();
	}

	public void setOldStakeAmount(long oldStakeAmount) {
		this.stakeChange = this.stakeChange.toBuilder()
				.setOldStakeAmount(oldStakeAmount)
				.build();
	}

	public boolean hasOldStakeAmount() {
		return getOldStakeAmount() >= 0;
	}

	public boolean hasNewStakeAmount() {
		return getNewStakeAmount() >= 0;
	}

	public void clearNewStakeAmount() {
		this.stakeChange = this.stakeChange.toBuilder()
				.setNewStakeAmount(-1)
				.build();
	}

}
