/**
 * Dianping.com Inc.
 * Copyright (c) 2003-2013 All Rights Reserved.
 */
package com.dianping.pigeon.remoting.invoker.route.balance;

import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.apache.log4j.Logger;

import com.dianping.pigeon.config.ConfigManagerLoader;
import com.dianping.pigeon.log.LoggerLoader;
import com.dianping.pigeon.remoting.common.domain.InvocationRequest;
import com.dianping.pigeon.remoting.common.util.Constants;
import com.dianping.pigeon.remoting.invoker.Client;
import com.dianping.pigeon.remoting.invoker.config.InvokerConfig;
import com.dianping.pigeon.remoting.invoker.exception.ServiceUnavailableException;
import com.dianping.pigeon.remoting.invoker.route.statistics.ServiceStatisticsHolder;
import com.dianping.pigeon.remoting.invoker.route.support.RouterHelper;

public abstract class AbstractLoadBalance implements LoadBalance {

	private static final Logger logger = LoggerLoader.getLogger(AbstractLoadBalance.class);

	protected Random random = new Random();

	private static int defaultFactor = ConfigManagerLoader.getConfigManager().getIntValue(
			"pigeon.loadbalance.defaultFactor", 100);

	@Override
	public Client select(List<Client> clients, InvokerConfig<?> invokerConfig, InvocationRequest request) {
		if (clients == null || clients.isEmpty()) {
			return null;
		}
		Client selectedClient = null;
		String forceAddress = RouterHelper.getAddress();
		if (forceAddress != null && forceAddress.length() > 0) {
			// 客户端强制路由
			for (Client client : clients) {
				if (forceAddress.equals(client.getAddress())) {
					selectedClient = client;
					break;
				}
			}
			if (selectedClient == null) {
				throw new ServiceUnavailableException("server[" + forceAddress + "] is not connected for service["
						+ request.getServiceName() + "].");
			}
		} else {
			if (clients.size() == 1) {
				selectedClient = clients.get(0);
			} else {
				try {
					selectedClient = doSelect(clients, invokerConfig, request,
							getWeights(clients, request.getServiceName()));
				} catch (Throwable e) {
					logger.error("Failed to do load balance[" + getClass().getName() + "], detail: " + e.getMessage()
							+ ", use random instead.", e);
					selectedClient = clients.get(random.nextInt(clients.size()));
				}
			}
		}
		if (selectedClient != null) {
			int weight = LoadBalanceManager.getEffectiveWeight(selectedClient.getAddress());
			request.setAttachment(Constants.REQ_ATTACH_FLOW, 1.0f * defaultFactor
					/ (weight > 0 ? weight : defaultFactor));
		}
		if (logger.isDebugEnabled()) {
			if (ServiceStatisticsHolder.checkRequestNeedStat(request)) {
				logger.debug("total requests to " + selectedClient.getAddress() + " in last second:"
						+ ServiceStatisticsHolder.getCapacityBucket(selectedClient.getAddress()).getLastSecondRequest());
			}
		}
		return selectedClient;
	}

	/**
	 * [w1, w2, w3, maxWeightIndex]
	 * 
	 * @param clients
	 * @param serviceName
	 * @param weightAccessor
	 * @return
	 */
	private int[] getWeights(List<Client> clients, String serviceName) {
		int clientSize = clients.size();
		int[] weights = new int[clientSize + 1];
		int maxWeightIdx = 0;
		int maxWeight = Integer.MIN_VALUE;
		for (int i = 0; i < clientSize; i++) {
			int effectiveWeight = LoadBalanceManager.getEffectiveWeight(clients.get(i).getAddress());
			weights[i] = effectiveWeight;
			if (weights[i] > maxWeight) {
				maxWeight = weights[i];
				maxWeightIdx = i;
			}
		}
		weights[clientSize] = maxWeightIdx;
		if (logger.isDebugEnabled()) {
			logger.debug("effective weights: " + Arrays.toString(weights));
		}
		return weights;
	}

	protected abstract Client doSelect(List<Client> clients, InvokerConfig<?> invokerConfig, InvocationRequest request,
			int[] weights);

}
