package com.wobgames.whosnext;

import java.util.ArrayList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.widget.Toast;

import com.wobgames.whosnext.ButtonsFragment.OnButtonSelectedListener;
import com.wobgames.whosnext.DeviceListFragment.OnCreateGroupListener;
import com.wobgames.whosnext.QuestionsFragment.OnStartGameSelectedListener;

public class MainActivity extends FragmentActivity implements OnButtonSelectedListener, OnStartGameSelectedListener, 
	PeerListListener, OnCreateGroupListener, ConnectionInfoListener {
	
	// ^(?!dalvikvm)
	
	// Debug
	public static final String TAG = "MainActivity";
	public static final String EXTRA_MESSAGE = "com.wobgames.whosnext.MESSAGE";
	public static final int NUMBER_OF_QUESTIONS = 15;
	
	/********** Member data **********/
	// Fragments
	ButtonsFragment mButtonsFragment;
	QuestionsFragment mQuestionsFragment;
	GameMainFragment mGameMainFragment;
	ImageFragment mImageFragment;
	DeviceListFragment mDeviceListFragment;
	
	// Objects
	public GameDevice mGameDevice = null;
	public DatabaseHelper mDBHelper;
	public Answer currentAnswer;
	public List<User> currentUsers;
	
	// Game Info
	public User currentUser;
	
	// WiFi p2p
	WifiP2pManager mManager;
	Channel mChannel;
	BroadcastReceiver mReceiver = null;
	private IntentFilter mIntentFilter;
	private boolean isWifiP2pEnabled = false;
	private boolean threadStarted = false;
	WifiP2pDevice mDevice = null;
	List<WifiP2pDevice> mPeers;
	private int peersCounter;
	public int totalPeers;
	public List<GameDevice> connectedDevices;
	public int SERVER_PORT = 8888;
	public ServerSocketHelper sHelper;
	public ClientSocketHelper cHelper;
	public boolean peersRemaining = true;
	public WifiP2pInfo connectionInfo;
	/*********************************/
	
	/** On Create() **/
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        
        if (savedInstanceState == null) {
			// Add the fragment on initial activity setup
        	mButtonsFragment = new ButtonsFragment();
			getSupportFragmentManager().beginTransaction().add(R.id.rootlayout, mButtonsFragment).commit();
		} else {
			// Or set the fragment from restored state info
			mButtonsFragment = (ButtonsFragment) getSupportFragmentManager().findFragmentById(R.id.rootlayout);
		}
        
        // Other fragments
        mQuestionsFragment = new QuestionsFragment();
        mGameMainFragment = new GameMainFragment();
        mImageFragment = new ImageFragment();
        mDeviceListFragment = new DeviceListFragment();
        
        // Create the Database
        mDBHelper = new DatabaseHelper(getApplication());
        mDBHelper.init();
        
        /** WiFiDirect **/
        
        // WiFi p2p Initial Setup
        mManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        mChannel = mManager.initialize(this, getMainLooper(), null);
        //mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        connectedDevices = new ArrayList<GameDevice>();
        peersCounter = 0;
        
        // Intent filter & intents
        mIntentFilter = new IntentFilter();
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
    }
    
    /** onResume() **/
    @Override
    public void onResume() {
        super.onResume();
        
        //resetData();
        // Register the broadcast receiver with same intent values
        mReceiver = new WiFiDirectBroadcastReceiver(mManager, mChannel, this);
        registerReceiver(mReceiver, mIntentFilter);
    }

    /** onPause() **/
    @Override
    public void onPause() {
        super.onPause();
        
        // Unregister the broadcast receiver
        unregisterReceiver(mReceiver);
    }
    
    /* ************ WiFiP2p Functions ************ */
    
    /**
     * @param isWifiP2pEnabled the isWifiP2pEnabled to set
     */
    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }
    
    public void updateDevice (WifiP2pDevice device) {
    	//Log.d(TAG, "updateThisDevice()");
    	
    	mDevice = device;
    	if(mGameDevice == null)
    	{
    		mGameDevice = new GameDevice(device);
    		//mGameDevice.setIsGroupOwner(device.isGroupOwner());
    	}
    }
    
    /**
     * Remove all peers and clear all fields. This is called on
     * BroadcastReceiver receiving a state change event.
     */
    public void resetData() {
    	Log.d(TAG, "resetData()");
        if (mDeviceListFragment != null && mDeviceListFragment.isAdded()) {
            mDeviceListFragment.clearPeers();
            Log.d(TAG, "resetData() - IF");
        }
    }
    
    // Connect to a single device from the list of peers
    public void connect(List<WifiP2pDevice> peers) {
    	
    	//Log.d(TAG, "Connect to peer: " + peers.get(peersCounter).deviceName + " - " + peersCounter);
    	
    	WifiP2pDevice peerDevice;
    	WifiP2pConfig config;
    	
    	// Connect to the device in the list of peers pointed by peersCounter
    	// if it is not already connected
    	if(mDeviceListFragment.getDeviceStatus(peers.get(peersCounter).status) == "Available")
    	{
    		peerDevice = peers.get(peersCounter);
    		config = new WifiP2pConfig();
    		config.deviceAddress = peerDevice.deviceAddress;
    		config.wps.setup = WpsInfo.PBC;
    		config.groupOwnerIntent = 15;   // Make current device group owner
    		
    		mManager.connect(mChannel, config, new ActionListener() {
    			
                @Override
                public void onSuccess() {
                    // WiFiDirectBroadcastReceiver will notify.
                }

                @Override
                public void onFailure(int reason) {
                    Toast.makeText(MainActivity.this, "Connect failed. Retry.",
                            Toast.LENGTH_SHORT).show();
                }
            });
    		
    		peersCounter++;
    	}
    	
    }
        
    /** PeerListListener **/
    @Override
    public void onPeersAvailable(WifiP2pDeviceList peerList) {
    	//Log.d(TAG, "onPeersAvailable()");
    	if(peersRemaining)
	    	if(mDeviceListFragment != null && mDeviceListFragment.isAdded()) {
	    		mDeviceListFragment.updatePeerList(peerList);
	    		mDeviceListFragment.updateThisDevice(mDevice);
	    	}
    }
    
    /** ConnectionInfoListener **/
    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {			// Check extra call for clients!!!!!
    	//Log.d(TAG, "onConnectionInfoAvailable");
    	
        // Group Owner (Server)
        if (info.groupFormed && info.isGroupOwner) {
            // Do the tasks that are specific to the group owner.
        	
        	Toast.makeText(MainActivity.this, "Connected as group owner.",
                    Toast.LENGTH_SHORT).show();
        	
        	mGameDevice.setIsGroupOwner(true);
        	// If the list of connected devices is empty, add the current device (group owner)
        	if(connectedDevices.isEmpty())
        	{
        		mGameDevice.setInfo(info);
        		connectedDevices.add(mGameDevice);
        	}
        }
        // Client (not group owner)
        else if (info.groupFormed) {
        	// If the list of connected devices is empty, add the current device (not group owner)
        	if(connectedDevices.isEmpty())
        	{
        		mGameDevice.setIsGroupOwner(false);
        		mGameDevice.setInfo(info);
        		connectedDevices.add(mGameDevice);
        	}
        	else
        	{
        		//Log.i(TAG, "ONE TIME, TWO TIMES???");
        		// Add the peer device to the list of connected devices
//            	GameDevice peerDevice = new GameDevice(mPeers.get(peersCounter-1));
//            	peerDevice.setInfo(info);
//            	connectedDevices.add(peerDevice);
        	}
        	
        	if(!threadStarted) {
	        	// Connect to the server socket
	        	cHelper = new ClientSocketHelper(this);
	        	cHelper.connect();
	        	threadStarted = true;
        	}
        	
        	getSupportFragmentManager().beginTransaction()
				.replace(R.id.rootlayout, mQuestionsFragment).commit();
        }
    }
    
    /* ************ Fragment Interfaces ************ */
    
    /**
     * onCreateGame() - ButtonsFragment
     */
    @Override
    public void onCreateGame() {
    	// When the user taps the Create Game button
    	//Log.d(TAG, "onCreateGame()");
    	
    	// Check if WiFiP2p is enabled
    	if (!isWifiP2pEnabled) {
            Toast.makeText(MainActivity.this, "Wi-Fi Direct is not enabled.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
    	
    	// Discover Peers
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Peers Discovery Initiated",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(MainActivity.this, "Peers Discovery Failed : " + reasonCode,
                        Toast.LENGTH_SHORT).show();
            }
        });
        
        // Create the server socket and threads
        sHelper = new ServerSocketHelper(this); 
    	sHelper.connect();
        
    	// Load DeviceList Fragment
    	getSupportFragmentManager().beginTransaction()
    		.replace(R.id.rootlayout, mDeviceListFragment).addToBackStack(null).commit();

    }
    
    /**
     * onJoinGame() - ButtonsFragment
     */
    @Override
    public void onJoinGame() {
    	
    	// When the user taps the Join Game button
    	//Log.d(TAG, "onJoinGame()");
    	
    	// Check if WiFiP2p is enabled
    	if (!isWifiP2pEnabled) {
            Toast.makeText(MainActivity.this, "Wi-Fi Direct is not enabled.",
                    Toast.LENGTH_SHORT).show();
            return;
        }
    	
    	// Discover Peers
        mManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {

            @Override
            public void onSuccess() {
                Toast.makeText(MainActivity.this, "Peers Discovery Initiated",
                        Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onFailure(int reasonCode) {
                Toast.makeText(MainActivity.this, "Peers Discovery Failed : " + reasonCode,
                        Toast.LENGTH_SHORT).show();
            }
        });
    	
    	// Load Image Fragment
    	getSupportFragmentManager().beginTransaction()
			.replace(R.id.rootlayout, mImageFragment).addToBackStack(null).commit();
    }
    
    /**
     * onStartGame() - GameMainFragment
     */
    @Override
    public void onStartGame() {
    	// When the user taps the Start Game button
    	//Log.d(TAG, "onStartGame()");
    	
    	// Load Image Fragment (waiting screen)
		getSupportFragmentManager().beginTransaction()
			.replace(R.id.rootlayout, mImageFragment).addToBackStack(null).commit();
		
		if(mGameDevice.isGroupOwner())
			sHelper.startGame();
    }
    
    /**
     * onConnect - DeviceListFragment
     * when the Connect button in the DeviceListFragment is pressed
     */
    public void onConnect() {
    	// Connect current device to all peers in the list, 
    	// and set it as group owner
    	//Log.d(TAG, "onConnect()");
    	
    	// Get the list of peer devices from the fragment
    	mPeers = new ArrayList<WifiP2pDevice>(mDeviceListFragment.getPeersList());
    	totalPeers = mPeers.size();
    	
    	if(mPeers.size() > 0 && peersCounter < totalPeers)
    		connect(mPeers);
    }
    
    /**
     * onStart - DeviceListFragment
     * When the button Start Game is pressed in DevieListFragment
     */
    public void onListStartGame() {
    	
    	getSupportFragmentManager().beginTransaction()
			.replace(R.id.rootlayout, mQuestionsFragment).commit();
    	
    }
    
    public void showToast(String toast) {
    	//Log.i(TAG, "showToast()");
    	Toast.makeText(MainActivity.this, toast,Toast.LENGTH_SHORT).show();
    }
    
    public void startTurn(Answer answer, List<User> users_list) {
    	currentAnswer = answer;
    	currentUsers = new ArrayList<User>(users_list);
    	Log.d(TAG, "startTurn");
    	
    	// Load Game Main Fragment
    	getSupportFragmentManager().beginTransaction()
			.replace(R.id.rootlayout, mGameMainFragment).addToBackStack(null).commit();
    }
}





