package fr.pantheonsorbonne.miage.jms;

import java.io.Closeable;
import java.io.IOException;
import java.io.StringReader;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.inject.Named;
import javax.jms.BytesMessage;
import javax.jms.Connection;
import javax.jms.ConnectionFactory;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;
import javax.xml.bind.JAXBContext;

import com.itextpdf.text.pdf.codec.Base64.InputStream;

import fr.pantheonsorbonne.miage.diploma.DiplomaGenerator;
import fr.pantheonsorbonne.miage.diploma.MiageDiplomaGenerator;
import fr.pantheonsorbonne.ufr27.miage.DiplomaInfo;

@ApplicationScoped
public class PdfGeneratorMessageHandler implements Closeable {

	@Inject
	@Named("diplomaRequests")
	private Queue requestsQueue;

	@Inject
	@Named("diplomaFiles")
	private Queue filesQueue;

	@Inject
	private ConnectionFactory connectionFactory;

	private Connection connection;
	private MessageConsumer diplomaRequestConsummer;
	private MessageProducer diplomaFileProducer;

	private Session session;

	@PostConstruct
	void init() {
		try {
			connection = connectionFactory.createConnection("nicolas", "nicolas");
			connection.start();
			session = connection.createSession();
			diplomaRequestConsummer = session.createConsumer(requestsQueue);
			diplomaFileProducer = session.createProducer(filesQueue);

		} catch (JMSException e) {
			throw new RuntimeException(e);
		}

	}

	public void consume() {
		
		try {
			// receive a text message from the consummer
			TextMessage txtMessage = (TextMessage) diplomaRequestConsummer.receive();
			// create a jaxbcontext, binding the DiplomaInfo class
			JAXBContext jxbContext = JAXBContext.newInstance(DiplomaInfo.class);
			// unmarshall the texte message body with the JaxBcontext
			DiplomaInfo diplomaInfo = (DiplomaInfo) jxbContext.createUnmarshaller().unmarshal(new StringReader(txtMessage.getText()));
			handledReceivedDiplomaSpect(diplomaInfo);
			// use the handleReceivedDiplomaSpect method to generate the diploma an send it through the wire
		}
		catch(Exception e) {
			System.out.println("Error consume");
		}

		
	}

	private void handledReceivedDiplomaSpect(DiplomaInfo diploma) {

		try {
			DiplomaGenerator generator = new MiageDiplomaGenerator(diploma.getStudent());
			InputStream is = (InputStream) generator.getContent();
			byte[] data = new byte[is.available()];
			is.read(data);
			this.sendBinaryDiploma(diploma, data);
			is.close();
		} catch (IOException e) {
			System.out.println("Error generate diploma");
		}

	}

	public void sendBinaryDiploma(DiplomaInfo info, byte[] data) {
		try {
			BytesMessage message = this.session.createBytesMessage();
			message.setIntProperty("id", info.getId());
			message.writeBytes(data);

			this.diplomaFileProducer.send(message);

		} catch (JMSException e) {
			System.out.println("Error send msg");
		}

	}

	@Override
	public void close() throws IOException {
		try {
			diplomaFileProducer.close();
			diplomaRequestConsummer.close();
			session.close();
			connection.close();
		} catch (JMSException e) {
			System.out.println("Failed to close JMS resources");
		}

	}

}
