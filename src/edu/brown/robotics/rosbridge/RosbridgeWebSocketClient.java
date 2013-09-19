package edu.brown.robotics.rosbridge;
//
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
//
import org.json.JSONException;
import org.json.JSONObject;
//
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class RosbridgeWebSocketClient extends WebSocketClient
{

	public RosbridgeWebSocketClient(URI serverURI) {
		super(serverURI);
		// TODO Auto-generated constructor stub
	}

	@Override
	public String getResourceDescriptor() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onOpen(ServerHandshake handshakedata){
	}

	@Override
	public void onMessage(String message) {
		System.out.println("Received: " + message);
	}

	@Override
	public void onClose(int code, String reason, boolean remote) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void onError(Exception ex) {
		System.err.println("WebSocketClient Error: " + ex.toString());
		
	}
	
	public void send(JSONObject msg)
	{
		this.send(msg.toString());
	}
	
}