package edu.brown.robotics.rosbridge;

import java.util.*;


import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONObject.*;
import org.json.JSONArray;
import org.json.JSONArray.*;

public class RosbridgeClient implements MessageHandler {
        public static void main(String [] args)
        {
                RosbridgeClient client = new RosbridgeClient("192.168.160.134", 9090);
                client.StatusMessage("Connecting to rosbridge server", "info");
                UUID subscribeID = client.Subscribe("something", client.PrintMessage);

                UUID publishID = client.Advertise("something", "String");
                for (int i = 0; i < 10; i++)
                {
                    client.Publish(publishID, i.ToString());
                }

                Thread.sleep(1000);
                client.Unsubscribe(subscribeID);
                client.Unadvertise(publishID);

        }



	private HashSet<UUID> ids;
	private HashMap<UUID, MessageHandler> handlers;
	private HashMap<UUID, String> publishedTopics;
	private HashMap<UUID, String> subscribedTopics;
	private HashMap<String, ArrayList<UUID>> subscriberLookup;
	private HashMap<String, String> topicTypes;
	
	
	private JavaClient client;
	
	public RosbridgeClient(String url, short port)
	{
		this.ids = new HashSet<UUID>();
		this.handlers = new HashMap<UUID, MessageHandler>();
		this.publishedTopics = new HashMap<UUID, String>();
		this.subscribedTopics = new HashMap<UUID, String>();
		this.subscriberLookup = new HashMap<String, ArrayList<UUID>>();
		this.topicTypes = new HashMap<String, String>();
		this.client = new JavaClient(url, port, this);
	}
	
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
	
	public void messageReceived(String msg) throws JSONException
	{
		JSONObject obj = new JSONObject(msg);
		ArrayList<UUID> ids = new ArrayList<UUID>();
		if (obj.has("id"))
		{
			String idStr = obj.getString("id");
			ids.add(UUID.fromString(idStr));
		}
		else
		{
			String subscriber = "";
			if (obj.has("topic"))
			{
				subscriber = obj.getString("topic");
			}
			else if (obj.has("service"))
			{
				subscriber = obj.getString("service");
			}
			
			if (subscriber != "")
			{
				ids.addAll(this.subscriberLookup.get(subscriber));
			}
		}
		for(UUID id : ids)
		{
			this.handlers.get(id).messageReceived(msg);
		}	
	}
	
	public UUID Subscribe(String topic, MessageHandler handler)
	{
		UUID id = this.createUniqueID();
		JSONObject call = new JSONObject();
		try {
		call.put("op", "subscribe");
		call.put("id", id.toString());
		call.put("topic", topic);
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to subscribe to " + topic + ": " + e);
		}
		this.client.sendPacket(call);
		this.subscribedTopics.put(id, topic);
		this.handlers.put(id, handler);
		if (!this.subscriberLookup.containsKey(topic))
		{
			this.subscriberLookup.put(topic, new ArrayList<UUID>());
		}
		this.subscriberLookup.get(topic).add(id);
		return id;
	}
	
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
		if (this.handlers.containsKey(id))
		{
			this.handlers.remove(id);
		}
		if (this.subscribedTopics.containsKey(id))
		{
			String topic = this.subscribedTopics.get(id);
			this.subscribedTopics.remove(id);
			if (this.subscriberLookup.containsKey(topic))
			{
				this.subscriberLookup.get(topic).remove(id);
			}
		}
		this.client.sendPacket(call);
	}
	
	public UUID Advertise(String topic, String type)
	{
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
		this.client.sendPacket(call);
		return id;
	}
	
	public void Unadvertise(UUID id)
	{
		JSONObject call = new JSONObject();
		try {
		call.put("op", "unadvertise");
		call.put("id", id.toString());
		call.put("topic", this.publishedTopics.get(id));
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to unadvertise: " + e);
		}
		if (this.publishedTopics.containsKey(id))
		{
			this.publishedTopics.remove(id);
		}
		this.client.sendPacket(call);
	}
	
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
		this.client.sendPacket(call);
	}
	
	public void CallService(String service, String[] args, MessageHandler handler)
	{
		JSONObject call = new JSONObject();
		try {
		call.put("op", "call_service");
		call.put("service", service);
		if (args.length > 0)
		{
			call.put("args", args);
		}
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to call service " + 
					service + ": " + e);
		}
		this.client.sendPacket(call);
	}
	
	public void ServiceResponse(UUID id, JSONArray values)
	{
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
		this.client.sendPacket(call);
	}
	
	public void StatusMessage(String message, String level)
	{
		JSONObject call = new JSONObject();
		try {
		call.put("op", "");
		call.put("level", level);
		call.put("message", message);
		}
		catch (JSONException e) {
			System.err.println("Unable to create JSON object to send status message: " + e);
		}
		this.client.sendPacket(call);
	}

        public void PrintMessage(String s)
        {
                System.out.println(s);
        }
}
