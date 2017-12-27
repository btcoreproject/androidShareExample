package btcore.co.kr.d2band.bluetoothchat;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import btcore.co.kr.d2band.R;

/**
 * Created by leehaneul on 2017-12-22.
 */

public class BluetoothChatFragment extends Fragment {

    private static final String TAG = "블루투스채팅 프래그먼트";

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    // 현재 연결중인 디바이스 네임
    private String mConnectedDeviceName = null;


    // 대화 스레드용 어댑터
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer for outgoing messages
     * 문자열을 추가하거나 변경 할 때 주료 사용하는 자료형
     */

    private StringBuffer mOutStringBuffer;


    /**
     * 로컬 블루투스 어댑터( 블루투스 송수신 장치) 를 나타낸다.
     * 블루투스 상호작용에 대한 진입점을 제공한다.
     * 어댑터를 사용하여 븥루투스 기기를 검색 연결된 (페어링된) 기기 목록을 쿼리하고 알려진 MAC 주소로 BluetoothDevice를 인스턴스화하고, 다른 기기로부터 통신을
     * 수신 대기하는 BluetoothServerSocket을 만들 수 있다.
     */
    private BluetoothAdapter mBluetoothAdapter = null;
    private BluetoothChatService mChatService = null;

    @Override
    public void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        //프래그먼트에서 옵션 메뉴 호출
        setHasOptionsMenu(true);

        /**
         * 블루투스 어댑터 가져오는 부분 블루투스 어댑터를 가져오기 위해서는 정적 getDefaultAdapter() 메서드를 호출
         * 이렇게 되면 기기의 자체 블루투스 어댑터를 나타내는 BluetoothAdapter가 반환됩니다.  null 을 반환하는 경우 블루투스를 지원하지 않는다.
         */
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // 장치가 블루투스를 지원하지 않는 경우
        if(mBluetoothAdapter == null){
            FragmentActivity activity = (FragmentActivity) getActivity();
            Snackbar.make(getActivity().getWindow().getDecorView().getRootView(), "블루투스 지원하지 않습니다.", Snackbar.LENGTH_LONG).show();
            activity.finish();
        } else{
          // 장치가 블루투스를 지원하는 경우
        }

    }

    @Override
    public void onStart(){
        super.onStart();
        /**
         * 블루투스 활성화 isEnabled() 함수를 호출하여 블루투스가 현재 활성화 되어 있는지 여부를 확인한다.
         * 이메서드가 false를 반환하는경우 블루투스는 비활성화 된다.
         * 블루투스 활성화 요청을 하려면 ACTION_REQUEST_ENABLE 작업 인텐트를 사용하여 startActivityForResult()를 호출합니다.
         * 그러면 ( 앱을 중지 않고 ) 시스템 설정을 통한 블루투스 활성화 요청이 발급된다.
         */
        if(!mBluetoothAdapter.isEnabled()){
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            /**
             * startActivityForResult 다른 액티비티를 시작하고 그 액티비티로부터 결과를 수신 할 수 있게 해주는 방법
             */
        }else if(mChatService == null){
            setupChat();
        }
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        // 블루투스 서비스 종료
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT was
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        // 만약 현재 서비스가 꺼져 있고 현재 상태가 None 상태이면 블루투스 서비스를 재시작 한다.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }



    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mConversationView = (ListView) view.findViewById(R.id.in);
        mOutEditText = (EditText) view.findViewById(R.id.edit_text_out);
        mSendButton = (Button) view.findViewById(R.id.button_send);
    }

    private void setupChat(){
        Log.d(TAG,"setupChat()");

        mConversationArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);

        mConversationView.setAdapter(mConversationArrayAdapter);

        mOutEditText.setOnEditorActionListener(mWriteListener);

        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                View view = getView();
                if(null != view){
                    TextView textView = (TextView) view.findViewById(R.id.edit_text_out);
                    String message = textView.getText().toString();
                    sendMessage(message);
                }
            }
        });

        mChatService = new BluetoothChatService(getActivity(), mHandler);

        // 버퍼 초기화
        mOutStringBuffer = new StringBuffer("");
    }

    /**
     * Makes this device discoverable for 300 seconds (5 minutes).
     * 로컬 기기를 다른 기기가 검색할 수 있게 하려면 ACTION_REQUEST_DISCOVERABLE 작업 인텐트를 사용하여 startActivity 을 호출
     * 그러면 애플리케이션이 중지하지 않고 시스템 설정을 통한 검색 가능 모드 활성화 요청이 발급된다.
     * 기본적으로 기기가 120초동안 검색 가능하게한다.
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private void sendMessage(String message){
        // 메시지를 보내기전에 커넥션이 되어 있는지 확인 하는 작업
        if(mChatService.getState() != BluetoothChatService.STATE_CONNECTED){
            Snackbar.make(getActivity().getWindow().getDecorView().getRootView(), "ITPANGPANG", Snackbar.LENGTH_LONG).show();
            return;
        }

        // 보낼 메시지가 있는지 확인하는 작업
        if(message.length() > 0){

            byte[] send = message.getBytes();
            mChatService.write(send);
            // 메시지를 전송후에 버퍼를 초기화 한후 Edittext 를 초기화 한다.
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };


    private void setStatus(int resId) {
        FragmentActivity activity = (FragmentActivity) getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = (FragmentActivity) getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }


    /**
     * The Handler that gets information back from the BluetoothChatService
     * BluetoothChatService 에 정보를  Handler 로 담는다.
     * Bluetooth 서비스에서 동작하는 또는 콜백 함수를 가지고 기능별 정의.
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = (FragmentActivity) getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case BluetoothChatService.STATE_CONNECTED:
                            setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                            mConversationArrayAdapter.clear();
                            break;
                        case BluetoothChatService.STATE_CONNECTING:
                            setStatus(R.string.title_connecting);
                            break;
                        case BluetoothChatService.STATE_LISTEN:
                            setStatus("STATE_LISTEN");
                        case BluetoothChatService.STATE_NONE:
                            setStatus(R.string.title_not_connected);
                            break;
                    }
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };

    /**
     * startActivityForResult()에 전달한 요청 코드 를 가지고 처리하는 부분.
     * @param requestCode
     * @param resultCode
     * @param data
     */

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
        }
    }

    // 원격장치화 커넥션 하는 함수
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.insecure_connect_scan: {
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
        }
        return false;
    }



}
