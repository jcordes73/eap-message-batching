package org.jboss.perf.test.service;

import java.io.StringReader;

import javax.annotation.Resource;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.inject.Inject;
import javax.jms.Destination;
import javax.jms.JMSConnectionFactory;
import javax.jms.JMSConsumer;
import javax.jms.JMSContext;
import javax.jms.Message;
import javax.jms.TextMessage;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;

import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.jboss.logging.Logger;
import org.jboss.perf.test.model.Member;

@Stateless
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class MemberBatchConsumer {

	private static final Logger log = Logger.getLogger(MemberBatchConsumer.class);

	@PersistenceContext(unitName = "perfTest")
	EntityManager em = null;

	@Inject
	@JMSConnectionFactory("java:/JmsXA")
	private JMSContext jmsContext;
	
	@Resource(lookup = "java:jboss/exported/jms/queue/member")
	private Destination memberQueue;

	Unmarshaller jaxbUnmarshaller = null;

	/**
	 * Default constructor.
	 * 
	 * @throws JAXBException
	 */
	public MemberBatchConsumer() throws JAXBException {
		JAXBContext jaxbContext = JAXBContext.newInstance(Member.class);
		jaxbUnmarshaller = jaxbContext.createUnmarshaller();
	}

	/**
	 * @throws Exception 
	 * 
	 */
	public void processBatch(int batchSize, long receiveTimeout) throws Exception {

		try {
			JMSConsumer consumer = jmsContext.createConsumer(memberQueue);
			
			Session session = (Session) em.getDelegate();
			session.setFlushMode(FlushMode.MANUAL);

			log.debug("Starting to consume " + batchSize + " messages.");

			for (int i = 0; i < batchSize; i++) {
				Message message = consumer.receive(receiveTimeout);

				if (message instanceof TextMessage) {
					log.debug("Received message nr. " + i);

					Member m = (Member) jaxbUnmarshaller.unmarshal(new StringReader(((TextMessage) message).getText()));

					em.persist(m);
				} else if (message == null) {
					break;
				}

				log.debug("Message nr. " + i + " processed.");
			}
			
			session.flush();

		} catch (Exception e) {
			log.error("Got error, rolling back batch", e);
			throw e;
		}
	}
}