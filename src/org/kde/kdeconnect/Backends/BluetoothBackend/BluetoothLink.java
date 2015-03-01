/*
 * Copyright 2014 Saikrishna Arcot <saiarcot895@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License or (at your option) version 3 or any later version
 * accepted by the membership of KDE e.V. (or its successor approved
 * by the membership of KDE e.V.), which shall act as a proxy
 * defined in Section 14 of version 3 of the license.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>. 
*/

package org.kde.kdeconnect.Backends.BluetoothBackend;

import android.bluetooth.BluetoothSocket;
import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.Device;
import org.kde.kdeconnect.NetworkPackage;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.security.PublicKey;

public class BluetoothLink extends BaseLink {
    private final BluetoothSocket socket;
    private final BluetoothLinkProvider linkProvider;

    private boolean continueAccepting = true;

    private Thread receivingThread = new Thread(new Runnable() {
        @Override
        public void run() {
            int character;
            StringBuilder sb = new StringBuilder();
            while (continueAccepting) {
                while (sb.indexOf("\n") == -1 && continueAccepting) {
                    try {
                        character = socket.getInputStream().read();
                        if (character != -1) {
                            sb.append((char)character);
                        }
                    } catch (IOException e) {
                        Log.e("BluetoothLink", "Connection to " + socket.getRemoteDevice().getAddress() + " likely broken.");
                        disconnect();
                        return;
                    }
                }

                int endIndex = sb.indexOf("\n");
                if (endIndex != -1) {
                    String message = sb.substring(0, endIndex + 1);
                    sb.delete(0, endIndex + 1);
                    processMessage(message);
                }
            }
        }

        private void processMessage(String message) {
            NetworkPackage np = NetworkPackage.unserialize(message);

            if (np.getType().equals(NetworkPackage.PACKAGE_TYPE_ENCRYPTED)) {

                try {
                    np = np.decrypt(privateKey);
                } catch(Exception e) {
                    e.printStackTrace();
                    Log.e("onPackageReceived","Exception reading the key needed to decrypt the package");
                }

            }

            packageReceived(np);
        }
    });

    public BluetoothLink(BluetoothSocket socket, String deviceId, BluetoothLinkProvider linkProvider) {
        super(deviceId, linkProvider);
        this.socket = socket;
        this.linkProvider = linkProvider;
        receivingThread.start();
    }

    public void disconnect() {
        if (socket == null) {
            return;
        }
        continueAccepting = false;
        try {
            socket.close();
        } catch (IOException e) {
        }
        linkProvider.disconnectedLink(this, getDeviceId(), socket);
    }

    private boolean sendMessage(NetworkPackage np) {
        try {
            byte[] message = np.serialize().getBytes(Charset.forName("UTF-8"));
            OutputStream socket = this.socket.getOutputStream();
            Log.e("BluetoothLink","Beginning to send message");
            socket.write(message);
            Log.e("BluetoothLink","Finished sending message");
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("BluetoothLink", "Exception with message upload");
            return false;
        }
    }

/*
    private void sendPayload(NetworkPackage np) {
        try {
            OutputStream socket = server.getOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;
            Log.e("BluetoothLink","Beginning to send payload");
            while ((bytesRead = stream.read(buffer)) != -1) {
                socket.write(buffer, 0, bytesRead);
            }
            socket.write(EOT_BYTES);
            Log.e("BluetoothLink","Finished sending payload");
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("BluetoothLink", "Exception with payload upload");
        }
    }
*/

    @Override
    public void sendPackage(NetworkPackage np, Device.SendPackageStatusCallback callback) {
        sendPackageInternal(np, callback, null);
    }

    @Override
    public void sendPackageEncrypted(NetworkPackage np, Device.SendPackageStatusCallback callback, PublicKey key) {
        sendPackageInternal(np, callback, key);
    }

    private void sendPackageInternal(NetworkPackage np, final Device.SendPackageStatusCallback callback, PublicKey key) {

        /*if (!isConnected()) {
            Log.e("BluetoothLink", "sendPackageEncrypted failed: not connected");
            callback.sendFailure(new Exception("Not connected"));
            return;
        }*/

        if (np.hasPayload()) {
            Log.e("BluetoothLink", "Payload delivery not yet supported over Bluetooth.");
        }

        if (key != null) {
            try {
                np = np.encrypt(key);
            } catch (Exception e) {
                callback.sendFailure(e);
                return;
            }
        }

        boolean success = sendMessage(np);

        if (success) callback.sendSuccess();
        else callback.sendFailure(new Exception("Unknown exception"));
    }

/*
    public boolean isConnected() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            return socket.isConnected();
        } else {
            return true;
        }
    }
*/
}
