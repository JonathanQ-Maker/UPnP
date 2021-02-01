import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.traversal.DocumentTraversal;
import org.w3c.dom.traversal.NodeFilter;
import org.w3c.dom.traversal.NodeIterator;

import javax.xml.parsers.DocumentBuilderFactory;

import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;

public abstract class UPnP {
    public static ArrayList<Listener> listeners = new ArrayList<Listener>();
    public static ArrayList<AccessPoint> availableAccessPoints = new ArrayList<AccessPoint>();
    public static AccessPoint targetAccessPoint = null;
    public static String[] search_msgs;
    private static String[] search_types = new String[] { "urn:schemas-upnp-org:device:InternetGatewayDevice:1",
            "urn:schemas-upnp-org:service:WANIPConnection:1", "urn:schemas-upnp-org:service:WANPPPConnection:1" };

    static {
        ArrayList<String> m = new ArrayList<String>();
        for (String type : search_types) {
            m.add("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nST: " + type
                    + "\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\n\r\n");
        }
        search_msgs = m.toArray(new String[] {});
    }

    public static void FindAccessPoint() 
    {
        for (Inet4Address ip : getLocalIPs()) {
            for (String msg : search_msgs) {
                Listener l = new Listener(ip, msg);
                l.start();
                listeners.add(l);
            }
        }
        while (isSearching()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
            }
        }
    }

    public static boolean openTCPPort(int port)
    {
        if (targetAccessPoint == null)
            FindAccessPoint();
        return targetAccessPoint.openPort(port, true);
    }

    public static boolean closeTCPPort(int port)
    {
        if (targetAccessPoint == null)
            FindAccessPoint();
        return targetAccessPoint.closePort(port, true);
    }

    public static boolean openUDPPort(int port)
    {
        if (targetAccessPoint == null)
            FindAccessPoint();
        return targetAccessPoint.openPort(port, false);
    }

    public static boolean closeUDPPort(int port)
    {
        if (targetAccessPoint == null)
            FindAccessPoint();
        return targetAccessPoint.openPort(port, false);
    }

    public static boolean isPortTCPMapped(int port)
    {
        if (targetAccessPoint == null)
            FindAccessPoint();
        return targetAccessPoint.isMapped(port, true);
    }

    public static boolean isPortUDPMapped(int port)
    {
        if (targetAccessPoint == null)
            FindAccessPoint();
        return targetAccessPoint.isMapped(port, false);
    }

    public static Inet4Address[] getLocalIPs() {
        ArrayList<Inet4Address> localIps = new ArrayList<Inet4Address>();
        Enumeration<NetworkInterface> interfaces = null;
        try {
            interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface intface = interfaces.nextElement();
                if (!intface.isUp() || intface.isLoopback() || intface.isPointToPoint() || intface.isVirtual())
                    continue;
                Enumeration<InetAddress> addresses = intface.getInetAddresses();
                if (addresses == null)
                    continue;
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof InetAddress)
                        localIps.add((Inet4Address) address);
                }
            }
        } catch (Exception e) {
        }
        return localIps.toArray(new Inet4Address[] {});
    }

    public static boolean isSearching()
    {
        for (Listener l : listeners) {
            if (l.isAlive())
                return true;
        }
        return false;
    }
}

class Listener extends Thread {
    Inet4Address ip;
    String request;

    Listener(Inet4Address ip, String request) {
        this.ip = ip;
        this.request = request;
    }

    public void run() {
        byte[] buffer = request.getBytes();
        try {
            DatagramSocket socket = new DatagramSocket(new InetSocketAddress(ip, 0));
            socket.send(new DatagramPacket(buffer, buffer.length, new InetSocketAddress("239.255.255.250", 1900)));
            socket.setSoTimeout(5000);

            while (true) {
                try {
                    DatagramPacket received = new DatagramPacket(new byte[1536], 1536);
                    socket.receive(received);
                    foundAccessPoint(new AccessPoint(received.getData(), ip));
                    break;
                } catch (SocketTimeoutException e) {
                    UPnP.listeners.remove(this);
                    break;
                } catch (Throwable e) {

                }
            }
        } catch (Throwable e) {
        }
    }

    private void foundAccessPoint(AccessPoint accessPoint)
    {
        UPnP.availableAccessPoints.add(accessPoint);
        if (UPnP.targetAccessPoint == null)
            UPnP.targetAccessPoint = accessPoint;
    }
}

class AccessPoint {
    String controlURL, serviceType;
    Inet4Address ip;

    AccessPoint(byte[] data, Inet4Address ip) throws Exception {
        this.ip = ip;
        String location = null;
        String _data = new String(data);
        String[] tokens = _data.split("\n");
        for (String token : tokens) {
            token = token.trim();
            if (token.isEmpty() || token.startsWith("HTTP/1.") || token.startsWith("NOTIFY *"))
                continue;
            String name = token.substring(0, token.indexOf(':')),
                    val = token.length() >= name.length() ? token.substring(name.length() + 1).trim() : null;
            if (name.equalsIgnoreCase("location"))
                location = val;

        }
        if (location == null)
            throw new Exception("UPnP unsupported");
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(location);
        NodeList services = doc.getElementsByTagName("service");
        String controlURL = null, serviceType = null;
        for (int i = 0; i < services.getLength(); i++) {
            Node service = services.item(i);
            NodeList service_childs = service.getChildNodes();
            for (int n = 0; n < service_childs.getLength(); n++) {
                Node child = service_childs.item(n);
                if (child.getNodeName().trim().equalsIgnoreCase("controlURL"))
                    controlURL = child.getFirstChild().getNodeValue();
                if (child.getNodeName().trim().equals("serviceType"))
                    serviceType = child.getFirstChild().getNodeValue();
            }

        }
        if (serviceType.trim().toLowerCase().contains(":wanipconnection:") || serviceType.trim().toLowerCase().contains(":wanpppconnection:")) {
            this.serviceType = serviceType.trim();
            this.controlURL = controlURL.trim();
        }
        if (controlURL == null)
            throw new Exception("Unsupported Access point");

        int slash = location.indexOf("/", 7);
        if (slash == -1)
            throw new Exception("Unsupported Access point");

        location = location.substring(0, slash);
        if (!controlURL.startsWith("/"))
            controlURL = "/" + controlURL;
        controlURL = location + controlURL;
        this.controlURL = controlURL;
    }

    private Map<String, String> sendCommand(String action, Map<String, String> param)
            throws Exception
    {
        String soap = "<?xml version=\"1.0\"?>\r\n" + "<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" SOAP-ENV:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\">"
        + "<SOAP-ENV:Body>"
        + "<m:" + action + " xmlns:m=\"" + serviceType + "\">";
        if (param != null)
        {
            for(Map.Entry<String, String> entry : param.entrySet())
            {
                soap += "<" + entry.getKey() + ">" + entry.getValue() + "</" + entry.getKey() + ">";
            }
        }
        soap += "</m:" + action + "></SOAP-ENV:Body></SOAP-ENV:Envelope>";
        byte[] req = soap.getBytes();
        HttpURLConnection connection = (HttpURLConnection) new URL(controlURL).openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "text/xml");
        connection.setRequestProperty("SOAPAction", "\"" + serviceType + "#" + action + "\"");
        connection.setRequestProperty("Connection", "Close");
        connection.setRequestProperty("Content-Length", "" + req.length);
        connection.getOutputStream().write(req);
        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(connection.getInputStream());
        NodeIterator iterator = ((DocumentTraversal) doc).createNodeIterator(doc.getDocumentElement(), NodeFilter.SHOW_ELEMENT, null, true);
        Node carrier;
        Map<String, String> response = new HashMap<>();
        while ((carrier = iterator.nextNode()) != null)
        {
            try 
            {
                if (carrier.getFirstChild().getNodeType() == Node.TEXT_NODE)
                    response.put(carrier.getNodeName(), carrier.getFirstChild().getTextContent());
            } catch (Throwable e) 
            {
            }
        }
        connection.disconnect();
        return response;
    }

    public boolean openPort(int port, boolean TCP)
    {
        if (port < 0 || port > 65535)
            throw new IllegalArgumentException("Invalid port! Acceptable port: 1-65534");
        Map<String, String> params = new HashMap<String, String>();
        params.put("NewRemoteHost", "");
        params.put("NewProtocol", TCP ? "TCP" : "UDP");
        params.put("NewInternalClient", ip.getHostAddress());
        params.put("NewExternalPort", "" + port);
        params.put("NewInternalPort", "" + port);
        params.put("NewEnabled", "1");
        params.put("NewPortMappingDescription", "WaifUPnP");
        params.put("NewLeaseDuration", "0");
        try {
            Map<String, String> r = sendCommand("AddPortMapping", params);
            return r.get("errorCode") == null;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean closePort(int port, boolean TCP) {
        if (port < 0 || port > 65535)
            throw new IllegalArgumentException("Invalid port! Acceptable port: 1-65534");
        Map<String, String> params = new HashMap<String, String>();
        params.put("NewRemoteHost", "");
        params.put("NewProtocol", TCP ? "TCP" : "UDP");
        params.put("NewExternalPort", "" + port);
        try {
            sendCommand("DeletePortMapping", params);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isMapped(int port, boolean TCP) {
        if (port < 0 || port > 65535)
            throw new IllegalArgumentException("Invalid port! Acceptable port: 1-65534");
        Map<String, String> params = new HashMap<String, String>();
        params.put("NewRemoteHost", "");
        params.put("NewProtocol", TCP ? "TCP" : "UDP");
        params.put("NewExternalPort", "" + port);
        try {
            Map<String, String> r = sendCommand("GetSpecificPortMappingEntry", params);
            if (r.get("errorCode") != null) {
                throw new Exception();
            }
            System.out.println(r.get("NewInternalPort"));
            return r.get("NewInternalPort") != null;
        } catch (Exception ex) {
            return false;
        }

    }
}