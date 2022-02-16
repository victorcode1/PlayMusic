package com.soft.playmusic;

import android.os.RemoteException;

public interface ServiceStub {
    void prev(boolean forcePrevious) throws RemoteException;
}
