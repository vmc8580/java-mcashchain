package io.midasprotocol.core.net.peer;

import io.midasprotocol.core.config.Parameter.NetConstants;
import io.midasprotocol.core.net.TronNetDelegate;
import io.midasprotocol.protos.Protocol.ReasonCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class PeerStatusCheck {

	@Autowired
	private TronNetDelegate tronNetDelegate;

	private ScheduledExecutorService peerStatusCheckExecutor = Executors
		.newSingleThreadScheduledExecutor();

	private int blockUpdateTimeout = 20_000;

	public void init() {
		peerStatusCheckExecutor.scheduleWithFixedDelay(() -> {
			try {
				statusCheck();
			} catch (Throwable t) {
				logger.error("Unhandled exception", t);
			}
		}, 5, 2, TimeUnit.SECONDS);
	}

	public void close() {
		peerStatusCheckExecutor.shutdown();
	}

	public void statusCheck() {

		long now = System.currentTimeMillis();

		tronNetDelegate.getActivePeer().forEach(peer -> {

			boolean isDisconnected = false;

			if (peer.isNeedSyncFromPeer()
				&& peer.getBlockBothHaveUpdateTime() < now - blockUpdateTimeout) {
				logger.warn("Peer {} not sync for a long time.", peer.getInetAddress());
				isDisconnected = true;
			}

			if (!isDisconnected) {
				isDisconnected = peer.getAdvInvRequest().values().stream()
					.anyMatch(time -> time < now - NetConstants.ADV_TIME_OUT);
			}

			if (!isDisconnected) {
				isDisconnected = peer.getSyncBlockRequested().values().stream()
					.anyMatch(time -> time < now - NetConstants.SYNC_TIME_OUT);
			}

			if (isDisconnected) {
				peer.disconnect(ReasonCode.TIME_OUT);
			}
		});
	}

}
