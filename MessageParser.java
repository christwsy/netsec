import java.util.*;
import java.lang.*;
import java.io.*;
import java.net.*;
import java.security.*;
import java.math.*;

public class MessageParser {
    //Monitor Handling Declarations
    int COMMAND_LIMIT = 25;
    public  int CType;
    public static String HOSTNAME;
    PrintWriter out = null;
    BufferedReader in = null;
    String mesg,sentmessage;
    String filename;
    StringTokenizer t;
    String IDENT = "Skipper";
    String PASSWORD = "franco";
    static String COOKIE ="default";
    String PPCHECKSUM="";
    int HOST_PORT;
    public static int IsVerified;

    //File I/O Declarations
    BufferedReader fIn = null;
    PrintWriter fOut = null;
    static String InputFileName = "Input.dat";
    static String ResourceFileName = "Resources.dat";
    String[] cmdArr = new String[COMMAND_LIMIT];

    static String PubKey;
    String MonitorKey;
    ObjectInputStream oin = null;
    ObjectOutputStream oout = null;

    DiffieHellmanExchange dfe;
    BigInteger sharedSecret;
    Karn karn;

    ZKP zkp;
    int ROUNDS = 8;

    public MessageParser() {
        filename = "passwd.dat";
        GetIdentification(); // Gets Password and Cookie from 'passwd.dat' file
    }

    public MessageParser(String ident, String password) {
        filename = ident + ".dat";
        PASSWORD = password;
        IDENT = ident;
        GetIdentification(); // Gets Password and Cookie from 'passwd.dat' file
        dfe = new DiffieHellmanExchange();
        try {
            PubKey = dfe.getDHParmMakePublicKey("DHKey").toString(32);
        } catch (Exception e) {
            System.out.println("Error making public key");
            System.out.println(e);
        }
        zkp = new ZKP();
    }

    // assign class variables from what is being
    // read from the monitor
    public void handleMsg(String msg) {
        if (msg.startsWith("RESULT: ROUNDS")) {
            ROUNDS = Integer.parseInt(msg.split(" ")[2]);
            zkp.setRounds(ROUNDS);
        } else if (msg.startsWith("RESULT: AUTHORIZE_SET")) {
            zkp.saveAuthSet(msg);
        } else if (msg.startsWith("RESULT: SUBSET_A")) {
            System.out.println("Made it to subset a");
            zkp.saveSubsetA(msg);
        } else if (msg.startsWith("RESULT: SUBSET_K")) {
            zkp.saveSubsetKJ(msg);
        }
    }

    //TODO
    public String GetMonitorMessage() {
        String sMesg="", decrypt="";
        try {
            String temp = "";

            //After IDENT has been sent-to handle partially encrypted msg group
            while(!(decrypt.trim().equals("WAITING:"))) {
                temp = in.readLine();
                //System.out.println("temp: " + temp);
                if (temp.startsWith("RESULT: IDENT")) {
                    MonitorKey = temp.split(" ")[2];
                    //System.out.println("Creating shared secret");
                    sharedSecret = dfe.getSecret(MonitorKey);
                    //System.out.println("Creating karn object");
                    karn = new Karn(sharedSecret);
                    decrypt = temp;
                }
                else if (karn != null) {
                    try {
                        //System.out.println("decrypting temp: " + temp);
                        decrypt = karn.decrypt(temp);
                        //System.out.println("decrypt: " + decrypt);
                    } catch (Exception e) {
                        decrypt = temp;
                        //System.out.println("not decrpyted: " + decrypt);
                    }
                    sMesg = sMesg.concat(" ");
                    sMesg = sMesg.concat(decrypt);
                }
                else {
                    decrypt = temp;
                    sMesg = sMesg.concat(" ");
                    sMesg = sMesg.concat(decrypt);
                }
            }
            sMesg = sMesg.trim();
            handleMsg(sMesg);
            return sMesg;   //sMesg now contains the Message Group sent by the Monitor
        } catch (IOException e) {
            System.out.println("MessageParser [getMonitorMessage]: error "+
                "in GetMonitorMessage:\n\t"+e+this);
            sMesg="";
        } catch (NullPointerException n) {
            System.out.println("NULL POINTER OH NOOOOOOOOOOOOOOO");
            sMesg = "";
        } catch (NumberFormatException o) {
            System.out.println("MessageParser [getMonitorMessage]: number "+
                "format error:\n\t"+o);
            sMesg="";
        } catch (NoSuchElementException ne) {
            System.out.println("MessageParser [getMonitorMessage]: no such "+
            "element exception occurred:\n\t"+this);
        } catch (ArrayIndexOutOfBoundsException ae) {
            System.out.println("MessageParser [getMonitorMessage]: AIOB "+
            "EXCEPTION!\n\t"+this);
            sMesg="";
        }
        System.out.println("returning smesg");
        return sMesg;
    }

    //Handling Cookie and PPChecksum
    public String GetNextCommand (String mesg, String sCommand) {
        try {
            String sDefault = "REQUIRE";
            if (!(sCommand.equals(""))) sDefault = sCommand;
            t = new StringTokenizer(mesg," :\n");
            //Search for the REQUIRE Command
            String temp = t.nextToken();
            while (!(temp.trim().equals(sDefault.trim()))) temp = t.nextToken();
            temp = t.nextToken();
            //System.out.println("MessageParser [getNextCommand]: returning:\n\t"+
            //    temp);
            return temp;  //returns what the monitor wants
        } catch (NoSuchElementException e) {  return null;  }
    }

    public boolean Login() {
        boolean success = false;
        try {
            String msg = GetMonitorMessage();
            String next = GetNextCommand(msg, "");
            System.out.println("msg: " + msg);
            System.out.println("next: " + next + "\n");

            if (next != null) {
                do {
                    Execute(next);
                    msg = GetMonitorMessage();
                    next = GetNextCommand(msg, "");
                    System.out.println("msg: " + msg);
                    System.out.println("next: " + next + "\n");
                } while (next != null && !next.equals("QUIT"));
                success = true;
            }
        } catch (NullPointerException n) {
            System.out.println("MessageParser [Login]: null pointer error "+
            "at login:\n\t"+n);
        }
        return success;
    }

    //Handle Directives and Execute appropriate commands with one argument
    public boolean Execute (String sentmessage, String arg) {
        boolean success = false;
        try {
            if (sentmessage.trim().equals("PARTICIPANT_HOST_PORT")) {
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat(arg);
                sentmessage = karn.encrypt(sentmessage);
                SendIt(sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("CHANGE_PASSWORD")) {
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat(PASSWORD);
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat(arg);
                sentmessage = karn.encrypt(sentmessage);
                SendIt(sentmessage);
                success = true;
            }
        } catch (IOException e) {
            System.out.println("IOError:\n\t"+e);
            success = false;
        } catch (NullPointerException n) {
            System.out.println("Null Error has occured");
            success=false;
        }
        return success;
    }

    //Handle Directives and Execute appropriate commands
    public boolean Execute (String sentmessage) {
        boolean success = false;
        try {
            System.out.println("sentmessage: " + sentmessage.trim() + "\n");
            if (sentmessage.trim().equals("IDENT")) {
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat(IDENT);
                sentmessage = sentmessage.concat(" ");
                try {
                    PubKey = dfe.getDHParmMakePublicKey("DHKey").toString(32);
                } catch (Exception e) {
                    System.out.println("Error making public key");
                    System.out.println(e);
                }
                sentmessage = sentmessage.concat(PubKey);
                SendIt (sentmessage.trim());
                success = true;
            } else if (sentmessage.trim().equals("PASSWORD")) {
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat(PASSWORD);
                sentmessage = karn.encrypt(sentmessage);
                SendIt (sentmessage.trim());
                success = true;
            } else if (sentmessage.trim().equals("HOST_PORT")) {
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat(HOSTNAME);
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat(String.valueOf(HOST_PORT));
                sentmessage = karn.encrypt(sentmessage);
                SendIt (sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("ALIVE")) {
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat(COOKIE);
                sentmessage = karn.encrypt(sentmessage);
                SendIt (sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("QUIT")) {
                sentmessage = karn.encrypt(sentmessage);
                SendIt(sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("SIGN_OFF")) {
                sentmessage = karn.encrypt(sentmessage);
                SendIt(sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("GET_GAME_IDENTS")) {
                sentmessage = karn.encrypt(sentmessage);
                SendIt(sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("PARTICIPANT_STATUS")) {
                sentmessage = karn.encrypt(sentmessage);
                SendIt(sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("RANDOM_PARTICIPANT_HOST_PORT")){
                sentmessage = karn.encrypt(sentmessage);
                SendIt(sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("PUBLIC_KEY")) {
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat(zkp.v.toString(32));
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat(zkp.n.toString(32));
                sentmessage = karn.encrypt(sentmessage);
                SendIt (sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("ROUNDS")) {
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat(Integer.toString(ROUNDS));
                sentmessage = karn.encrypt(sentmessage);
                zkp.setRounds(ROUNDS);
                SendIt (sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("AUTHORIZE_SET")) {
                zkp.doRounds();
                for (int i=0; i<zkp.rounds.length; i++) {
                    sentmessage = sentmessage.concat(" ");
                    sentmessage = sentmessage.concat(zkp.rounds[i].toString(32));
                }
                sentmessage = karn.encrypt(sentmessage);
                SendIt (sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("SUBSET_A")) {
                zkp.genSubsetA();
                for (int i=0; i<zkp.subsetA.length; i++) {
                    sentmessage = sentmessage.concat(" ");
                    sentmessage = sentmessage.concat(Integer.toString(zkp.subsetA[i]));
                }
                System.out.println("\n\n\n" + sentmessage + "\n\n\n\n");
                sentmessage = karn.encrypt(sentmessage);
                SendIt (sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("SUBSET_K")) {
                zkp.calcSubsetK();
                for (int i=0; i<zkp.subsetK.length; i++) {
                    sentmessage = sentmessage.concat(" ");
                    sentmessage = sentmessage.concat(zkp.subsetK[i].toString(32));
                }
                sentmessage = karn.encrypt(sentmessage);
                SendIt (sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("SUBSET_J")) {
                zkp.calcSubsetJ();
                for (int i=0; i<zkp.subsetJ.length; i++) {
                    sentmessage = sentmessage.concat(" ");
                    sentmessage = sentmessage.concat(zkp.subsetJ[i].toString(32));
                }
                sentmessage = karn.encrypt(sentmessage);
                SendIt (sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("TRANSFER_RESPONSE")) {
                sentmessage = sentmessage.concat(" ");
                if (zkp.checkSubsets()) {
                    sentmessage = sentmessage.concat("ACCEPT");
                } else {
                    sentmessage = sentmessage.concat("DECLINE");
                }
                System.out.println("TRANSFER RESULT: " + sentmessage);
                sentmessage = karn.encrypt(sentmessage);
                //SendIt (sentmessage);
                success = true;
            } else if (sentmessage.trim().equals("TRANSFER_REQUEST")) {
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat("DIRKY123");
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat("10");
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat("FROM");
                sentmessage = sentmessage.concat(" ");
                sentmessage = sentmessage.concat("SIYUE");
                sentmessage = karn.encrypt(sentmessage);
                SendIt (sentmessage);
                success = true;
            } else {
                System.out.println("Got: " + sentmessage);
            }
        } catch (IOException e) {
            System.out.println("IOError:\n\t"+e);
            success = false;
        } catch (NullPointerException n) {
            System.out.println("Null Error has occured");
            success=false;
        }
        return success;
    }

    public void SendIt (String message) throws IOException {
        try {
            //System.out.println("MessageParser [SendIt]: sent:\n\t"+message);
            out.println(message);
            if (out.checkError() == true) throw (new IOException());
            out.flush();
            if(out.checkError() == true) throw (new IOException());
        } catch (IOException e) {} //Bubble the Exception upwards
    }

    //In future send parameters here so that diff commands are executed
    public boolean ProcessExtraMessages() {
        boolean success = false;
        System.out.println("MessageParser [ExtraCommand]: received:\n\t"+
        mesg.trim());

        if ((mesg.trim().equals("")) || (mesg.trim().equals(null))) {
            mesg = GetMonitorMessage();
            System.out.println("MessageParser [ExtraCommand]: received (2):\n\t"+
            mesg.trim());
        }

        String id = GetNextCommand (mesg, "");

        if (id == null) { // No Require, can Launch Free Form Commands Now
            if (Execute("PARTICIPANT_STATUS")) { //Check for Player Status
                mesg = GetMonitorMessage();
                success = true;
                try {
                    SaveResources(mesg);  //Save the data to a file
                    SendIt("SYNTHESIZE WEAPONS");
                    mesg = GetMonitorMessage();
                    SendIt("SYNTHESIZE COMPUTERS");
                    mesg = GetMonitorMessage();
                    SendIt("SYNTHESIZE VEHICLES");
                    mesg = GetMonitorMessage();
                    if (Execute("PARTICIPANT_STATUS")) { //Check for Player Status
                        mesg = GetMonitorMessage();
                        success = true;
                        SaveResources(mesg);//Save the data to a file
                    }
                } catch (IOException e) {}
            }
        } else {
            mesg = GetMonitorMessage();
            System.out.println("MessageParser [ExtraCommand]: failed "+
            "extra message parse");
        }
        return success;
    }

    public void MakeFreeFlowCommands() throws IOException {
    }

    public void SaveResources(String res) throws IOException {
        System.out.println("MessageParser [SaveResources]:");
        try {  // If an error occurs then don't update the Resources File
            String temp = GetNextCommand (res, "COMMAND_ERROR");
            if ((temp == null) || (temp.equals(""))) {
                fOut = new PrintWriter(new FileWriter(ResourceFileName));
                t = new StringTokenizer(res," :\n");
                try {
                    temp = t.nextToken();
                    temp = t.nextToken();
                    temp = t.nextToken();
                    System.out.println("MessageParser [SaveResources]: got "+
                    "token before write: "+temp);
                    for (int i=0 ; i < 20 ; i++) {
                        fOut.println(temp);
                        fOut.flush();
                        temp = t.nextToken();
                    }
                } catch (NoSuchElementException ne) {
                    temp = "";
                    fOut.close();
                }
            }
            fOut.close();
        } catch (IOException e) { fOut.close(); }
    }

    public void HandleTradeResponse(String cmd) throws IOException {
    }

    public boolean IsTradePossible(String TradeMesg) {
        return false;
    }

    public int GetResource(String choice) throws IOException {
        return 0;
    }

    public void HandleWarResponse(String cmd) throws IOException{
    }

    public void DoTrade(String cmd)  throws IOException{
    }

    public void DoWar(String cmd)  throws IOException{
    }

    public void ChangePassword(String newpassword) {
        GetIdentification(); //Gives u the previous values of Cookie and Password
        Execute("CHANGE_PASSWORD", newpassword);
        String msg = GetMonitorMessage();
        if (msg.startsWith("RESULT: CHANGE_PASSWORD")) {
            COOKIE = msg.split(" ")[2].trim();
            WritePersonalData(newpassword, COOKIE);
        }
    }

    public void GetIdentification() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(filename));
            String line = "";
            String lastLine = "";

            while ((line = reader.readLine()) != null) {
                if (lastLine.equals("PASSWORD")) {
                    this.PASSWORD = line;
                }
                else if (lastLine.equals("COOKIE")) {
                    this.COOKIE = line;
                }
                lastLine = line;
            }
        } catch (FileNotFoundException e) {
            System.out.println("File " + filename + " not found, continuing.");
        } catch (IOException e) {
            System.out.println("IOException when reading " + filename + " .");
        }
    }

    // Write Personal data such as Password and Cookie
    public boolean  WritePersonalData(String Passwd,String Cookie) {
        boolean success = false;
        PrintWriter pout = null;
        try {
            if ((Passwd != null) && !(Passwd.equals(""))) {
                pout = new PrintWriter(new FileWriter(filename));
                pout.println("PASSWORD");
                pout.println(Passwd); //(PASSWORD);
            }
            if ((Cookie != null) && !(Cookie.equals(""))) {
                pout.println("COOKIE");
                pout.flush();
                pout.println(Cookie);
                pout.flush();
            }
            pout.close();
            success = true;
        } catch (IOException e) {
            pout.close();
            return success;
        } catch (NumberFormatException n) {
        }
        return success;
    }

    //Check whether the Monitor is Authentic
    public boolean Verify(String passwd,String chksum) {
        return false;
    }

    public boolean IsMonitorAuthentic(String MonitorMesg) {
        return false;
    }
}
