package edu.brown.robotics.rosbridge;

import java.io.UnsupportedEncodingException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.json.JSONObject;
import org.jwebsocket.api.WebSocketClientEvent;
import org.jwebsocket.api.WebSocketPacket;
import org.jwebsocket.client.token.BaseTokenClient;
import org.jwebsocket.api.WebSocketClientListener;
import org.jwebsocket.kit.RawPacket;
import org.jwebsocket.kit.WebSocketException;
import org.jwebsocket.packetProcessors.JSONProcessor;
import org.jwebsocket.token.Token;

public class JavaClient implements WebSocketClientListener {

	private BaseTokenClient client;
	private String socketAddress;
	private Boolean open;
	private MessageHandler packetHandler;
	
	public JavaClient(String url, short port, MessageHandler handler)
	{
		this.client = new BaseTokenClient();
        this.client.addListener(this);       
        this.Open(url, port);
        this.packetHandler = handler;
	}
	
	public void Open()
	{
		try {
            this.client.open(this.socketAddress);
        } catch (WebSocketException ex) {
            Logger.getLogger(JavaClient.class.getName()).log(Level.SEVERE, null, ex);
        }
		try {
            this.client.login(null, null);
        } catch (WebSocketException ex) {
            Logger.getLogger(JavaClient.class.getName()).log(Level.SEVERE, null, ex);
        }
	}
	
	public void Open(String url, short port)
	{
		this.socketAddress = url + ":" + port;
		this.Open();
		
	}
	
	public void Close()
	{
		this.client.close();
	}
	
	public void sendPacket(JSONObject packet) {
	    try {
	        client.broadcastText(packet.toString());
	    } catch (WebSocketException ex) {
	        Logger.getLogger(JavaClient.class.getName()).log(Level.SEVERE, null, ex);
	    }
	}
        
	@Override
	public void processClosed(WebSocketClientEvent e) {
		this.open = false;
		
	}

	@Override
	public void processOpened(WebSocketClientEvent e) {
		this.open = true;
		
	}

	@Override
	public void processOpening(WebSocketClientEvent e) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void processPacket(WebSocketClientEvent e, WebSocketPacket packet) {
		try {
			String s = packet.getString("data");
			this.packetHandler.messageReceived(s);
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		
	}

	@Override
	public void processReconnecting(WebSocketClientEvent e) {
		// TODO Auto-generated method stub
		
	}
	
}
