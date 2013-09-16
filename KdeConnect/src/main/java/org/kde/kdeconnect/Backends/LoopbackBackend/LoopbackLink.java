package org.kde.kdeconnect.Backends.LoopbackBackend;

import android.util.Log;

import org.kde.kdeconnect.Backends.BaseLink;
import org.kde.kdeconnect.Backends.BaseLinkProvider;
import org.kde.kdeconnect.NetworkPackage;

import java.security.PublicKey;

public class LoopbackLink extends BaseLink {

    public LoopbackLink(BaseLinkProvider linkProvider) {
        super("loopback", linkProvider);
    }

    @Override
    public boolean sendPackage(NetworkPackage in) {
        String s = in.serialize();
        NetworkPackage out= NetworkPackage.unserialize(s);
        packageReceived(out);
        return true;
    }

    @Override
    public boolean sendPackageEncrypted(NetworkPackage in, PublicKey key) {
        try {
            in.encrypt(key);
            String s = in.serialize();
            NetworkPackage out= NetworkPackage.unserialize(s);
            out.decrypt(privateKey);
            packageReceived(out);
            return true;
        } catch(Exception e) {
            e.printStackTrace();
            Log.e("LoopbackLink", "Encryption exception");
            return false;
        }

    }
}
