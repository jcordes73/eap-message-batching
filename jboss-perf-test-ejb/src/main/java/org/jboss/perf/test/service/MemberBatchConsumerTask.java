package org.jboss.perf.test.service;

import org.jboss.logging.Logger;

public class MemberBatchConsumerTask extends Thread {

	private static final Logger log = Logger.getLogger(MemberBatchConsumerTask.class);
	
	private MemberBatchConsumer memberConsumer;
	private int batchSize;
	private long receiveTimeout;

	
	public MemberBatchConsumerTask(String name, MemberBatchConsumer memberConsumer, int batchSize, long receiveTimeout) {
		this.setName(name);
		this.setPriority(MAX_PRIORITY);
		this.memberConsumer = memberConsumer;
		this.batchSize = batchSize;
		this.receiveTimeout = receiveTimeout;
	}
	
	@Override
	public void run() {
		try {
			while (true) {
				long start = System.currentTimeMillis();
				log.debug("Calling memberConsumer from thread " + this.getName());
				memberConsumer.processBatch(batchSize, receiveTimeout);
				long end = System.currentTimeMillis();
				log.debug("Calling memberConsumer from thread " + this.getName() + " has taken " + (end-start) + " ms");
			}
		} catch (Exception e) {
			log.error("Error in  MemberBatchConsumerTask", e);
		}
	}
	
}
