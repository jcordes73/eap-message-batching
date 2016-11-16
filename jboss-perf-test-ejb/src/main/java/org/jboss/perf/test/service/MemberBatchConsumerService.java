package org.jboss.perf.test.service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.ejb.EJB;
import javax.ejb.Singleton;
import javax.ejb.Startup;
import javax.enterprise.concurrent.ManagedThreadFactory;

import org.jboss.logging.Logger;

@Singleton
@Startup
public class MemberBatchConsumerService {

	private static final Logger log = Logger.getLogger(MemberBatchConsumerService.class);

	@Resource(name = "DefaultManagedThreadFactory")
	ManagedThreadFactory factory;

	private int consumerCount = 100;
	private int batchSize = 200;
	private long receiveTimeout = 50;

	@EJB
	MemberBatchConsumer memberConsumer;

	@PostConstruct
	public void init() {
		for (int i = 0; i < consumerCount; i++) {
			String threadName = "MemberBatchConsumerThread-" + i;
			Thread thread = factory.newThread(new MemberBatchConsumerTask(threadName, memberConsumer, batchSize, receiveTimeout));
			thread.start();
			log.info("Started thread MemberBatchConsumerThread-" + i);
		}
	}

	@PreDestroy
	public void releaseResources() {
	}

}
