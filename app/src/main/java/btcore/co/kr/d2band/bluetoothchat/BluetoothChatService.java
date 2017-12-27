package btcore.co.kr.d2band.bluetoothchat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

import android.os.Handler;

/**
 * Created by leehaneul on 2017-12-22.
 */

public class BluetoothChatService {
    private static final String TAG = "BluetoothChatService";

    // 서버 소켓을 만들때 사용할 레코드 이름
    private static final String NAME_SECURE = "BluetoothChatSecure";
    private static final String NAME_INSECURE = "BluetoothChatInsecure";

    /**
     * 븥루투스 UUID 란 범용 고유번호 라고 불리며 128bit의 숫자들을 조합한다.
     * 128비트의 HEX 조합은 Unique하여야 한다. Bluetooth 에서는 device 에서 제공하는 service를 검색하여 각 service 마다
     * UUID 를 부여한다.
     * < UUID 구성요소 >
     * UUID = (time_low) - (time_mid) - (time_high_and_version) - (clock_seq_hi_and_reserved) - (clock_seq_low - node)
     */
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66");
    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");

    // 멤버 변수
    private final BluetoothAdapter mAdapter;
    // 기본 생성자를 통해 Handler를 생성하면, 생성되는  Handler 는 해당 Handler 를 호출한 스레드의  MessageQueue 와 Looper에 자동 연결된다.
    private final Handler mHandler;
    private AcceptThread mSecureAcceptThread;
    private AcceptThread mInsecureAcceptThread;
    private ConnectThread mConnectThread;
    private ConnectedThread mConnectedThread;
    private int mState;
    private int mNewState;


    // 커넥션 상태를 나타낸다.
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_LISTEN = 1;     // now listening for incoming connections
    public static final int STATE_CONNECTING = 2; // now initiating an outgoing connection
    public static final int STATE_CONNECTED = 3;  // now connected to a remote device

    /**
     * 블루투스 생성자
     *
     * @param context
     * @param handler
     */
    public BluetoothChatService(Context context, Handler handler) {


        this.mAdapter = BluetoothAdapter.getDefaultAdapter();
        // 초기 생성자로 커넥션 상태는 NONE
        this.mState = STATE_NONE;
        this.mNewState = mState;
        this.mHandler = handler;


    }

    /**
     * 스레드 동기화 synchronized
     * 채팅 연결이 되면 현재 상태에 따라 UI 제목을 업데이트 한다 ( 동기화)
     */
    private synchronized void updateUserInterfaceTitle() {
        mState = getState();
        // 현재 상태가 어떻게 바뀌었는지 로그 출력
        Log.d(TAG, "updateUserInterfaceTitle() " + mNewState + " -> " + mState);
        // UI 액티비티를 업데이트 할 수 있도록 핸들로에 새로운 상태를 지정
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, mNewState, -1).sendToTarget();

    }

    /**
     * 현재 커넥션 상태를 리턴
     */
    public synchronized int getState() {
        return mState;
    }

    public synchronized void start() {
        Log.d(TAG, "start");

        // 연결을 시도하는 모든 쓰레드를 취소한다.
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // 현재 커넥션 이 맺어 있는 상태라면 커넥션을 종료한다.
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }
        // Start the thread to listen on a BluetoothServerSocket
        if (mSecureAcceptThread == null) {
            mSecureAcceptThread = new AcceptThread(true);
            mSecureAcceptThread.start();
        }
        if (mInsecureAcceptThread == null) {
            mInsecureAcceptThread = new AcceptThread(false);
            mInsecureAcceptThread.start();
        }

        // 현재 상태 동기화
        updateUserInterfaceTitle();
    }

    /**
     * ConnectThread 를 시작하여 원격 장치에 대한 연결을 시도한다.
     *
     * @param device
     * @param secure
     */
    public synchronized void connect(BluetoothDevice device, boolean secure) {
        Log.d(TAG, "connect to " + device);

        if (mState == STATE_CONNECTING) {
            if (mConnectThread != null) {
                mConnectThread.cancel();
                mConnectThread = null;
            }
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // 지정된 장치에 연결하기 위해 스레드를 시작한다.
        mConnectThread = new ConnectThread(device, secure);
        mConnectThread.start();

        updateUserInterfaceTitle();

    }

    /**
     * Start the ConnectedThread to begin managing a Bluetooth connection
     *
     * @param socket The BluetoothSocket on which the connection was made
     * @param device The BluetoothDevice that has been connected
     */
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice
            device, final String socketType) {
        Log.d(TAG, "connected, Socket Type:" + socketType);

        // Cancel the thread that completed the connection
        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        // Cancel any thread currently running a connection
        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        // Cancel the accept thread because we only want to connect to one device
        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }
        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        mConnectedThread = new ConnectedThread(socket, socketType);
        mConnectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, device.getName());
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        // Update UI title
        updateUserInterfaceTitle();
    }

    /**
     * Stop all threads
     */
    public synchronized void stop() {
        Log.d(TAG, "stop");

        if (mConnectThread != null) {
            mConnectThread.cancel();
            mConnectThread = null;
        }

        if (mConnectedThread != null) {
            mConnectedThread.cancel();
            mConnectedThread = null;
        }

        if (mSecureAcceptThread != null) {
            mSecureAcceptThread.cancel();
            mSecureAcceptThread = null;
        }

        if (mInsecureAcceptThread != null) {
            mInsecureAcceptThread.cancel();
            mInsecureAcceptThread = null;
        }
        mState = STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();
    }
    /**
     * Write to the ConnectedThread in an unsynchronized manner
     *
     * @param out The bytes to write
     * @see ConnectedThread#write(byte[])
     */
    public void write(byte[] out) {
        // Create temporary object
        ConnectedThread r;
        // Synchronize a copy of the ConnectedThread
        synchronized (this) {
            if (mState != STATE_CONNECTED) return;
            r = mConnectedThread;
        }
        // Perform the write unsynchronized
        r.write(out);
    }

    /**
     * 이스레드는 들어오는 연결을 수신하는 동안 실행되며 행동한다.
     * 서버 - 클라이언트 와 비슷한 형태로 동작
     * 연결이 수락 또는 취소 될때 까지 실행됩니다.
     *
     * 이 스레드에서는 애플리케이션은 연결이 수락되고 BluetoothSocket을 가져오자마자 가져온 BluetoothSocket을 개별 스레드로 보내고 BluetoothServerSocket을 닫고 해당 루프를 중단합니다.
     * accept() 가 BluetoothSocket 을 반환할때 해당 소켓은 이미 연결되어 있으므로 connect를 호출해서는 안된다.
     */
    private class AcceptThread extends Thread {

        //The Local Sever Socet
        private final BluetoothServerSocket mmServerSocket;
        private String mSocketType;

        // 생성자 ( 리스닝 하는 서버 소켓 생성자.)
        public AcceptThread(boolean secure) {
            BluetoothServerSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";

            try {
                if (secure) {
                    tmp = mAdapter.listenUsingRfcommWithServiceRecord(NAME_SECURE, MY_UUID_SECURE);
                } else {
                    tmp = mAdapter.listenUsingInsecureRfcommWithServiceRecord(NAME_INSECURE, MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.e(TAG, "Socet Type : " + mSocketType + "listen() failed", e);
            }
            mmServerSocket = tmp;
            mState = STATE_LISTEN;
        }

        public void run() {
            Log.d(TAG, "Socket Type" + mSocketType + "BEGIN mAcceptThread" + this);
            setName("AcceptThread" + mSocketType);

            BluetoothSocket socket = null;

            // 연결 되어 있지 않은 상태에서는 서버 소켓을 듣고 있는다.
            while (mState != STATE_CONNECTED) {
                try {
                    socket = mmServerSocket.accept();
                } catch (IOException e) {
                    Log.e(TAG, "Socket Type :" + mSocketType + "accept() failed", e);
                    break;
                }

                // 만약 커넥션 이 수용된 상태라면
                if (socket != null) {
                    synchronized (BluetoothChatService.this) {
                        switch (mState) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                connected(socket, socket.getRemoteDevice(), mSocketType);
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                    Log.e(TAG, "Could not close unwanted socket", e);
                                }
                                break;
                        }
                    }
                }
            }
            Log.i(TAG, "END mAcceptThread, socket Type: " + mSocketType);
        }
        public void cancel() {
            Log.d(TAG, "Socket Type" + mSocketType + "cancel " + this);
            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Socket Type" + mSocketType + "close() of server failed", e);
            }
        }
    }

    // 커넥션 취소했을때 불리는 함수
    private void connectionFailed() {
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Unable to connect deviece");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        mState = STATE_NONE;
        // Update UI title
        updateUserInterfaceTitle();
        // 다시 리스닝 모드 상태로 돌아간다.
        BluetoothChatService.this.start();
    }

    // 연결이 끊어 졌음을 알리고 UI 에 알림.
    private void connectionLost() {

        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "Device connection was lost");
        msg.setData(bundle);
        mHandler.sendMessage(msg);

        // UI 업데이트
        updateUserInterfaceTitle();

        // 블루투스를 리스닝 모드로 재시작한다.

        BluetoothChatService.this.start();
    }


    private class ConnectThread extends Thread {
        /**
         * 블루투스 소켓(tcp 소켓과 유사) 에 대한 인터페이스를 말한다. InputStream 및 OutputStream을 통해 애플리케이션이 다른 블루투스 기기와 데이터를 교환할 수 있게 허용하는 연결 지점입니다.
         */
        private final BluetoothSocket mmSocket;
        // 원격 블루투스 기기를 나타낸다. 이를 사용하여 BluetoothSocket 을 통해 원격 기기와의 연결을 요청하거나 이름, 주소 , 클래스 및 연결 상태와 같은 기기 정보를 쿼리할 수 있도록 한다.
        private final BluetoothDevice mmDevice;
        private String mSocketType;

        public ConnectThread(BluetoothDevice device, boolean secure) {
            // 연결된 디바이스에 정보를 저장.
            mmDevice = device;
            BluetoothSocket tmp = null;
            mSocketType = secure ? "Secure" : "Insecure";
            // 커넥션을 위한 블루투스 소켓을 얻는 과정
            // 블루투스 기기 줌
            try {
                if (secure) {
                    // createRfcommSocketToServiceRecord 을 호출해서 BluetoothSocket 을 얻는다. ( 내가 정의한 uuid 를 통해서)
                    tmp = device.createRfcommSocketToServiceRecord(MY_UUID_SECURE);
                } else {
                    // createInsecureRfcommSocketToServiceRecord 을 호출해서 블루투스 소켓을 얻는다.
                    tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                }
            } catch (IOException e) {
                Log.d(TAG, "Socet Type" + mSocketType + "create() failed", e);

            }
            mmSocket = tmp;
            mState = STATE_CONNECTING;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectThread SocketType:" + mSocketType);
            // 스래드 이름 부여 (디버깅의 편리함을 위해)
            setName("ConnectThread" + mSocketType);

            // 항상 스캔을 취소한뒤 연결한다 왜냐하면 커넥션 상태가 늦어지는 요소가 되기 때문이다.
            mAdapter.cancelDiscovery();

            // 커넥션 가능한 블루투스 소켓을 만든다.
            try {
                // 소켓 연결 시도.
                mmSocket.connect();
            } catch (IOException e) {
                try {
                    mmSocket.close();
                } catch (IOException e1) {
                    // 소켓 커넥션하는 시간 동안에 커넥션 되지 않으면 오류 출력
                    Log.e(TAG, "unable to close() " + mSocketType +
                            " socket during connection failure", e1);
                }
                connectionFailed();
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect " + mSocketType + " socket failed", e);
            }
        }

    }

    /**
     * 이 스레드는 원격 장치와의 연결 중에 실행된다.
     * 모든 송수신 전송을 처리한다.
     */
    private class ConnectedThread extends Thread {

        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;

        // 생성자
        public ConnectedThread(BluetoothSocket socket, String socketType) {
            Log.d(TAG, "Create ConnectedThread" + socketType);
            mmSocket = socket;
            InputStream tmpln = null;
            OutputStream tmpOut = null;

            // 블루투스 소켓 입출력 스트림 가져오기
            try {
                tmpln = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "temp sockets not created", e);
            }

            mmInStream = tmpln;
            mmOutStream = tmpOut;
            // 현재 연결 상태로 바꾸어준다.
            mState = STATE_CONNECTED;
        }

        public void run() {
            Log.i(TAG, "BEGIN mConnectedThread");
            byte[] buffer = new byte[1024];
            int bytes;

            // 연결된 상태에서 InputStream 계속 리스닝 상태로 둔다.
            while (mState == STATE_CONNECTED) {
                try {
                    // InputStream 에 데이터를 읽는다.
                    bytes = mmInStream.read(buffer);

                    mHandler.obtainMessage(Constants.MESSAGE_READ, bytes, -1, buffer)
                            .sendToTarget();

                } catch (IOException e) {
                    Log.e(TAG, "disconnected", e);
                    connectionLost();
                    break;
                }
            }

        }

        /**
         * mmOutStream 스트림에 버퍼에대한 내용을 쓴다.
         *
         * @param buffer
         */
        public void write(byte[] buffer) {

            try {
                mmOutStream.write(buffer);

                // 보낸 메시지를 다시 공유 UI 로 보여준다.
                mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, buffer).sendToTarget();
            } catch (IOException e) {
                Log.e(TAG, "Exception during write", e);
            }
        }

        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "close() of connect socket failed", e);
            }
        }

    }

}
