/**
 * 
 * @author Stephen Brawner
 * Brown University
 *
 */

package edu.brown.robotics.rosbridge;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONObject.*;
import org.json.JSONArray;
import org.json.JSONArray.*;
import org.java_websocket.client.WebSocketClient;

/**
 * The RosbridgeClient class represents the client that an application will interact with. This
 * class contains an example with a main method.
 */
public class RosbridgeClient implements MessageHandler {
	
	/**
	 * Main method for which serves as an example and test of the class.
	 * @param args Command line arguments.
	 */
    public static void main(String [] args) {
        RosbridgeClient client = new RosbridgeClient("127.0.0.1", (short)9090);
        int wait = 0;
        try {
            while (!client.client.isOpen() && wait++ < 100) {
            	System.out.println("Waiting to connect: " + wait );
            	Thread.sleep(1);
            }
        }
        catch (Exception e)
        {
        	System.err.println("Error sleeping");
        }
        //client.StatusMessage("Connecting to rosbridge server", "info");
        UUID subscribeID = client.Subscribe("/something", "std_msgs/String", client);

        UUID publishID = client.Advertise("something", "std_msgs/String");
        for (int i = 0; i < 10; i++) {
            try {
				client.Publish(publishID, new JSONObject("{'data': '" + i + "'}"));
			} catch (JSONException e) {
				System.err.println("Failed to stringify number " + i);
				e.printStackTrace();	
			}
        }

        try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			System.err.println("Thread failed sleeping");
			e.printStackTrace();
		}
        client.Unsubscribe(subscribeID);
        client.Unadvertise(publishID);
    }

    /**
     * A set of unique ids for subscribers and publishers
     */
	private HashSet<UUID> ids;
	
	/**
	 * A map of ids to MessageHandler callbacks. All services and topics require callbacks, and
	 * this serves as a lookup
	 */
	private HashMap<UUID, MessageHandler> handlers;
	
	/**
	 * For any lookup that requires getting the name of a topic that we're publishing to
	 */
	private HashMap<UUID, String> publishedTopics;
	
	/**
	 * For any lookup that requires getting the name of a topic that we're subscribing to
	 */
	private HashMap<UUID, String> subscribedTopics;
	
	/**
	 * This is a map of topics to subscriber IDs. Subscriber messages don't return an ID, which
	 * makes calling their lookup by our UUID method tricky. Instead we lookup the UUID's that 
	 * are associated with a given topic
	 */
	private HashMap<String, ArrayList<UUID>> subscriberLookup;
	
	/**
	 * This is a map of topics to types, saves service calls to /rosapi/topic_type
	 */
	private HashMap<String, String> topicTypes;
	
	/**
	 * Our implementation of the WebSocketClient
	 */
	private RosbridgeWebSocketClient client;

	/**
	 * Constructor
	 * @param url IP4 url of the rosbridge server
	 * @param port Desired port, typical default is 9090
	 */
	public RosbridgeClient(String url, short port) {
		this.ids = new HashSet<UUID>();
		this.handlers = new HashMap<UUID, MessageHandler>();
		this.publishedTopics = new HashMap<UUID, String>();
		this.subscribedTopics = new HashMap<UUID, String>();
		this.subscriberLookup = new HashMap<String, ArrayList<UUID>>();
		this.topicTypes = new HashMap<String, String>();
		
		try {
			this.client = new RosbridgeWebSocketClient(new URI("ws://" + url + ":" + port + "/"));
		} 
		catch (URISyntaxException e) {
			System.err.println("URI improperly formed. URL: " + url + " port: " + port);
			e.printStackTrace();
		}
	}
	
	/**
	 * Generates a unique UUID for subscriber/publisher lookups
	 * @return The generated UUID
	 */
	private UUID createUniqueID()
	{
		UUID id = UUID.randomUUID();
		while (this.ids.contains(id))
		{
			id = UUID.randomUUID();
		}
		this.ids.add(id);
		return id;
	}
	
	/**
	 * Callback for the WebSocketClient, this method will call the proper subscriber or service
	 * callbacks for each message. The string parameter msg is converted into JSONObject and the
	 * message field is passed to the proper callback.
	 * @param msg The received message as a string
	 * @throws JSONException Thrown if JSON parsing fails
	 */
	public void messageReceived(String msg) throws JSONException {
		JSONObject obj = new JSONObject(msg);
		ArrayList<UUID> ids = new ArrayList<UUID>();
		if (obj.has("id")) {
			String idStr = obj.getString("id");
			ids.add(UUID.fromString(idStr));
		}
		else {
			String subscriber = "";
			if (obj.has("topic")) {
				subscriber = obj.getString("topic");
			}
			else if (obj.has("service")) {
				subscriber = obj.getString("service");
			}
			
			if (subscriber != "") {
				ids.addAll(this.subscriberLookup.get(subscriber));
			}
		}
		for(UUID id : ids) {
			this.handlers.get(id).messageReceived(msg);
		}	
	}
	
	/**
	 * Creates a subscription to a topic on the ROS system.
	 * @param topic Topic to subscribe to on ROS system
	 * @param type The type of message of this topic. 
	 * @param handler The method to be called for each received message
	 * @return A uniquely generated ID for this subscription
	 */
	public UUID Subscribe(String topic, String type, MessageHandler handler) {
		UUID id = this.createUniqueID();
		JSONObject call = new JSONObject();

		try {
			call.put("op", "subscribe");
			call.put("id", id.toString());
			call.put("topic", topic);
			if (type != null || type != "") {
				call.put("type", type);
			}
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to subscribe to " + topic + ": " + e);
		}

		this.client.send(call);
		this.subscribedTopics.put(id, topic);
		this.handlers.put(id, handler);

		if (!this.subscriberLookup.containsKey(topic))
		{
			this.subscriberLookup.put(topic, new ArrayList<UUID>());
		}
		this.subscriberLookup.get(topic).add(id);
		return id;
	}
	
	/**
	 * Unsubscribes from a topic on the ROS system
	 * @param id The unique id of the subscriber that is to be unsubscribed
	 */
	public void Unsubscribe(UUID id)
	{
		JSONObject call = new JSONObject();
		try {
			call.put("op", "unsubscribe");
			call.put("id", id.toString());
			call.put("topic", this.subscribedTopics.get(id));
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to unsubscribe: " + e);
		}
		if (this.handlers.containsKey(id)) {
			this.handlers.remove(id);
		}
		if (this.subscribedTopics.containsKey(id)) {
			String topic = this.subscribedTopics.get(id);
			this.subscribedTopics.remove(id);
			if (this.subscriberLookup.containsKey(topic)) {
				this.subscriberLookup.get(topic).remove(id);
			}
		}
		this.client.send(call.toString());
	}
	
	/**
	 * This notifies the ROS system that a topic will be published to. It requires both a topic
	 * name and a topic type
	 * @param topic The name of the topic
	 * @param type The ROS message type of this topic
	 * @return The unique ID of this publisher.
	 */
	public UUID Advertise(String topic, String type) {
		UUID id = this.createUniqueID();
		JSONObject call = new JSONObject();
		try {
			call.put("op", "advertise");
			call.put("topic", topic);
			call.put("type", type);
			call.put("id", id.toString());
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to advertise topic " + 
					topic + ": " + e);
		}
		this.publishedTopics.put(id,  topic);
		this.topicTypes.put(topic, type);
		this.client.send(call.toString());
		return id;
	}
	
	/**
	 * Notifies the ROS system that this topic will no longer be published to
	 * @param id The unique ID of this publisher
	 */
	public void Unadvertise(UUID id) {
		JSONObject call = new JSONObject();

		try {
			call.put("op", "unadvertise");
			call.put("id", id.toString());
			call.put("topic", this.publishedTopics.get(id));
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to unadvertise: " + e);
		}
		if (this.publishedTopics.containsKey(id)) {
			this.publishedTopics.remove(id);
		}
		this.client.send(call.toString());
	}
	
	/**
	 * Publishes a message to a given topic. With this implementation, the topic must first be
	 * advertised
	 * @param id The unique ID returned from the Advertise method
	 * @param obj The JSONObject to publish
	 */
	public void Publish(UUID id, JSONObject obj)
	{
		JSONObject call = new JSONObject();
		try {
			call.put("op", "publish");
			call.put("topic", this.publishedTopics.get(id));
			call.put("msg", obj);
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to publish message " + 
					obj.toString() + ": " + e);
		}
		this.client.send(call.toString());
	}
	
	/**
	 * Calls a service on the ROS system with possibly arguments for the request message.
	 * The message will be called with the message upon receipt of the message service response
	 * @param service The name of the service to call
	 * @param args The arguments of a request, if null (or empty) then an empty request is sent
	 * @param handler The callback method for this service request
	 */
	public void CallService(String service, String[] args, MessageHandler handler)
	{
		JSONObject call = new JSONObject();
		try {
			call.put("op", "call_service");
			call.put("service", service);
			if (args != null && args.length > 0) {
				call.put("args", args);
			}
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to call service " + 
					service + ": " + e);
		}
		this.client.send(call.toString());
	}
	
	/**
	 * A response to a service request from the ROS system
	 * @param id The ID of the service that was requested
	 * @param values A JSONArray of values of the response
	 */
	public void ServiceResponse(UUID id, JSONArray values) {
		JSONObject call = new JSONObject();
		try {
			call.put("op", "service_response");
			call.put("id", id.toString());
			call.put("service", this.publishedTopics.get(id));
			call.put("values", values);
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to send service response: " + e);
		}
		this.client.send(call.toString());
	}
	
	/**
	 * Sends a status message to be displayed by the rosbridge server 
	 * @param message A message string to be displayed
	 * @param level The message status level ('info', 'debug', 'warning', 'error')
	 */
	public void StatusMessage(String message, String level)
	{
		JSONObject call = new JSONObject();
		try {
			call.put("op", "status");
			call.put("level", level);
			call.put("msg", message);
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to send status message: " + e);
		}
		this.client.send(call.toString());
	}
}