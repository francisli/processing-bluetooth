package com.francisli.processing.bluetooth;

import java.io.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.microedition.io.*;
import javax.bluetooth.*;
import javax.obex.ClientSession;
import javax.obex.HeaderSet;
import javax.obex.Operation;

/**
 * This originated from the Mobile Processing project - http://mobile.processing.org
 *
 * Ported to Processing by, http://www.extrapixel.ch/bluetooth/
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General
 * Public License along with this library; if not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA  02111-1307  USA
 *
 * @author  Francis Li
 * @author  extrapixel
 */
public class Service implements Runnable {
    public static final String UNKNOWN = "(Unknown)";
    
    public static final int ATTR_SERVICENAME    = 0x0100;
    public static final int ATTR_SERVICEDESC    = 0x0101;
    public static final int ATTR_PROVIDERNAME   = 0x0102;
    
    public Device           device;
    public ServiceRecord    record;
    public Bluetooth        bt;
    
    public String           name;
    public String           description;
    public String           provider;
    
    protected Service(Device device, ServiceRecord record, Bluetooth bt) {
        this.device = device;
        this.record = record;
        this.bt = bt;
        
        DataElement element;
        element = record.getAttributeValue(ATTR_SERVICENAME);
        if (element != null) {
            name = (String) element.getValue();
        } else {
            name = UNKNOWN;
        }
        
        element = record.getAttributeValue(ATTR_SERVICEDESC);
        if (element != null) {
            description = (String) element.getValue();
        } else {
            description = UNKNOWN;
        }
        
        element = record.getAttributeValue(ATTR_PROVIDERNAME);
        if (element != null) {
            provider = (String) element.getValue();
        } else {
            provider = UNKNOWN;
        }
    }
    
    public Client connect() {
        try {
            StreamConnection con = (StreamConnection) Connector.open(record.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false));
            Client c = new Client(con);
            c.device = device;
            c.open();
            return c;
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage());
        }
    }
    
    /** Sends a file using OBEX Object Push.
     * 
     * @param fileName 
     */
    public void send(String fileName, String fileMimeType) {
        File file = new File(fileName);
        if (!file.exists()) {
            file = bt.parent.sketchFile(fileName);
        }
        if (!file.exists()) {
            System.err.println("[bluetooth] Couldn't find fileName: " + fileName);
            return;
        }
        
        Connection con = null;
        try{
            String URL = record.getConnectionURL(ServiceRecord.NOAUTHENTICATE_NOENCRYPT, false);
            System.out.println("[bluetooth] Connecting to: " + this.device);
            System.setProperty("bluecove.obex.mtu", "4096");
            con = Connector.open(URL);
            
            ClientSession cs = (ClientSession)con;
            
            HeaderSet hs = cs.createHeaderSet();
            cs.connect(hs);
            hs.setHeader(HeaderSet.NAME, fileName);
            hs.setHeader(HeaderSet.TYPE, fileMimeType); 
            hs.setHeader(HeaderSet.LENGTH, new Long(file.length()));

            Operation put = cs.put(hs);
            InputStream is = null;
            OutputStream os = null;
            try {
                is = new FileInputStream(file);
                os = put.openOutputStream();
                byte[] data = new byte[4096];
                int bytesRead = is.read(data);
                int totalBytesRead = bytesRead;
                int totalBytes = (int) file.length();
                while (bytesRead >= 0) {
                    os.write(data, 0, bytesRead);
                    try {
                        this.bt.serviceSendProgressEvent(bytesRead, totalBytesRead, totalBytes);
                    } catch (Throwable t) {
                    }
                    bytesRead = is.read(data);
                    totalBytesRead += bytesRead;
                }
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException ex) {
                    }
                }
                if (os != null) {
                    try {
                        os.close();
                    } catch (IOException ex) {
                    }
                }
            }
            put.close();
        } catch (Exception e){
            System.err.println(e.getMessage());
        } finally {
            if (con != null) {
                try {
                    ((ClientSession)con).disconnect(null);
                } catch (IOException ex) {
                }
                try {
                    con.close();
                } catch (IOException ex) {
                }
            }
        }
    }
    
    /** 
     * This run() method is used to run the server thread, which accepts
     * client connections and dispatches them to the sketch.  The setup
     * occurs in Bluetooth.start().
     */
    public void run() {
        while (bt.serverThread == Thread.currentThread()) {
            try {
                StreamConnection con = bt.server.acceptAndOpen();
                Client c = new Client(con);
                c.device = new Device(RemoteDevice.getRemoteDevice(con), bt);
                try {
                    c.device.name = c.device.device.getFriendlyName(false);
                } catch (Exception e) {
                    c.device.name = null;
                }
                if (c.device.name == null) {
                    c.device.name = Device.UNKNOWN;
                }
                c.open();
                bt.clientConnectEvent(c);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe.getMessage());
            }
        }
        try {
            bt.server.close();
        } catch (IOException ioe) {            
        }
    }
}
