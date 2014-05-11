package net.peercoin;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import net.htmlparser.jericho.ShoutboxExtractor;
import net.htmlparser.jericho.Source;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.JoinEvent;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

public class Shoutbox2IRC {
	public static class SmfShoutboxParser {
		public static class Item {
			public final String user;
			public final String message;

			public Item(String user, String message) {
				super();
				if (user == null)
					throw new NullPointerException("user == null");
				if (message == null)
					throw new NullPointerException("message == null");
				this.user = user;
				this.message = message;
			}

			@Override
			public String toString() {
				return "Item [user=" + user + ", message=" + message + "]";
			}

			@Override
			public int hashCode() {
				int h = user.hashCode();
				return h + h * 31 * message.hashCode();
			}

			@Override
			public boolean equals(Object obj) {
				if (obj == null)
					return false;
				if (!(obj instanceof Item))
					return false;
				Item that = (Item) obj;
				return that.user.equals(user) && that.message.equals(message);
			}
		}

		DocumentBuilder dBuilder;

		public SmfShoutboxParser() {
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory
					.newInstance();
			try {
				dBuilder = dbFactory.newDocumentBuilder();
			} catch (ParserConfigurationException e) {
				throw new ExceptionInInitializerError(e);
			}
		}

		public List<Item> parse(InputSource source) throws SAXException,
				IOException {
			List<Item> items = new ArrayList<Item>();
			Document doc = dBuilder.parse(source);
			Element el = doc.getDocumentElement();
			el = (Element) el.getElementsByTagName("msgs").item(0);
			Source src = new Source(el.getTextContent());
			for (net.htmlparser.jericho.Element tr : src.getChildElements()) {
				List<net.htmlparser.jericho.Element> tds = tr
						.getChildElements();
				if (tds.size() < 2)
					continue;
				String user = tds.get(0).getFirstElement("a")
						.getTextExtractor().toString();
				String msg = ShoutboxExtractor.extract(tds.get(1));
				items.add(new Item(user, msg));
			}
			return items;
		}
	}

	public static class SmfShoutboxMessageSource {
		String url;
		Charset charset;
		String cookie;
		boolean init;
		SmfShoutboxParser parser;

		public SmfShoutboxMessageSource(String url, Charset encoding) {
			super();
			this.url = url;
			this.charset = encoding;
			this.init = true;
			this.parser = new SmfShoutboxParser();
		}

		@SuppressWarnings("unchecked")
		public List<SmfShoutboxParser.Item> get() throws MalformedURLException,
				IOException, SAXException {
			HttpURLConnection conn = (HttpURLConnection) new URL(
					this.cookie == null ? (url + ";restart") : url)
					.openConnection();
			if (this.cookie != null)
				conn.setRequestProperty("Cookie", cookie);
			String cookie = conn.getHeaderField("Set-Cookie");
			if (cookie != null)
				this.cookie = cookie.split(";")[0].trim();
			int contentLength = Integer.parseInt(conn
					.getHeaderField("Content-Length"));
			InputStream is = conn.getInputStream();
			if (contentLength < 30) {
				is.close();
				return Collections.EMPTY_LIST;
			}
			List<SmfShoutboxParser.Item> items = parser.parse(new InputSource(
					is));
			is.close();
			return items;
		}
	}

	public static void main(String[] args) throws Exception {
		if (args.length != 4) {
			System.err.println("usage:\n"
					+ "\tshoutbox2irc irc.freenode.net nick password '#channel'");
			System.exit(-1);
		}
		// IRC
		String server = args[0];
		String nick = args[1];
		String password = args[2];
		String channel = args[3];

		String urlStr = "http://www.peercointalk.org/index.php?action=shoutbox;sa=get;xml;row=20";
		SmfShoutboxMessageSource source = new SmfShoutboxMessageSource(urlStr,
				Charset.forName("utf-8"));

		final AtomicBoolean run = new AtomicBoolean(true), joined = new AtomicBoolean(
				false);

		final PircBotX bot = new PircBotX(new Configuration.Builder<PircBotX>()
				.setServerHostname(server)
				.setName(nick)
				.setLogin("LQ")
				.addAutoJoinChannel(channel)
				// .setCapEnabled(true)
				.addListener(new ListenerAdapter<PircBotX>() {
					@Override
					public void onJoin(JoinEvent<PircBotX> event)
							throws Exception {
						//event.getChannel().send().message("hello!");
						joined.set(true);
					}
				}).setNickservPassword(password).setName(nick)
				.setAutoNickChange(true).buildConfiguration());
		new Thread() {
			public void run() {
				try {
					bot.startBot();
				} catch (Exception e) {
					e.printStackTrace();
				}
			};
		}.start();

		new Thread() {
			public void run() {
				try {
					System.in.read();
				} catch (Exception e) {

				} finally {
					run.set(false);
				}
			};
		}.start();

		source.get();
		while (run.get()) {
			if (joined.get()) {
				for (SmfShoutboxParser.Item item : source.get())
					bot.sendIRC().message(channel,
							item.user + "- " + item.message);
			}
			Thread.sleep(10000);
		}
		bot.sendIRC().quitServer("bot death");
	}
}
