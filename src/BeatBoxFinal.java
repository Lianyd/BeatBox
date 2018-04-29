import java.awt.*;
import javax.swing.*;
import  javax.sound.midi.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.io.*;
import java.lang.reflect.Array;
import java.net.Socket;
import java.util.*;
import java.awt.event.*;

public class BeatBoxFinal {
    JFrame theFrame;
    JPanel mainPanel;
    JList incomingList;
    JTextField userMessage;
    ArrayList<JCheckBox> checkboxList;
    int nextNum;
    Vector<String> listVector = new Vector<String>();
    String userName;
    ObjectOutputStream out;
    ObjectInputStream in;
    HashMap<String ,boolean[]> otherSeqsMap = new HashMap<String ,boolean[]>();

    Sequencer sequencer;
    Sequence sequence;
    Sequence mySequence = null;
    Track track;


    String[] instrumentNames = {      // 乐器名称
            "Bass Drum","Closed Hi-Hat","Open Hi-Hat","Acoustic Snare",
            "Crash Cymbal","Hand Clap","High Tom","Hi Bongo",
            "Maracas","Whistle","Low Conga","Cowbell",
            "Vibraslap","Low-mid Tom","High Agogo","Open Hi Conga"};
    int[] instruments = {35,42,46,38,49,39,50,60,70,72,64,56,58,47,67,63};   // 不同乐器的关键字

    public static void main (String[] args){
        new BeatBoxFinal().startUp("BOB");   // args[0] is your user ID/screen name
    }

    public void startUp(String name){
        userName = name;
        // open connection to the server
        try{
            Socket sock = new Socket("127.0.0.1",4242);                            // 连接服务器IP（此处为本地IP），以及TCP端口
            out = new ObjectOutputStream(sock.getOutputStream()); //写给服务器的对象信息流
            in = new ObjectInputStream(sock.getInputStream());    //从服务器读取的对象信息流     //设置网络、输入/输出，并创建出 reader 的线程
            Thread remote = new Thread(new RemoteReader());       // 将读取信息功能放入新的线程中，具体实现方法在remotereader中
            remote.start();
        }catch (Exception ex){
            System.out.println("couldn't connect - you'll hava to play alone.");
        }
        setUpMidi();  // 创建播放器、CD、专辑、播放速度等
        buildGUI();   // 绘制主界面
    }

    public void buildGUI(){

        theFrame = new JFrame("Cyber BeatBoxFinal");
        theFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE); // 关闭界面后停止运行程序
        BorderLayout layout = new BorderLayout();
        JPanel background = new JPanel(layout); // 将background面板设置为layout布局,否则JPanel布局为flowlayout形式
        background.setBorder(BorderFactory.createEmptyBorder(10,10,10,10)); // 设定面板上摆设组件时的空白边缘

        checkboxList = new ArrayList<JCheckBox>();  // 用来存储每个方块的勾选情况

        Box buttonBox = new Box(BoxLayout.Y_AXIS);  // 用来存放交互按钮的box
        JButton start = new JButton("start");  // start按钮
        start.addActionListener(new MyStartListener());  // 按钮监听功能
        buttonBox.add(start);

        JButton stop = new JButton("stop");
        stop.addActionListener(new MyStopListener());
        buttonBox.add(stop);

        JButton upTempo = new JButton("Tempo Up");
        upTempo.addActionListener(new MyUpTempoListener());
        buttonBox.add(upTempo);

        JButton downTempo = new JButton("Tempo Down");
        downTempo.addActionListener(new MyDownTempoListener());
        buttonBox.add(downTempo);

        JButton save = new JButton("save");
//        Serializelt.addActionListener(new MySendListener());
//        buttonBox.add(Serializelt);
        buttonBox.add(save);

        JButton load = new JButton("load");
//        Restore.addActionListener(new MyReadInListener());
//        buttonBox.add(Restore);
        buttonBox.add(load);

        JButton sendIt = new JButton("sendIt");
        sendIt.addActionListener(new MySendListener());
        buttonBox.add(sendIt);

        userMessage = new JTextField();
        buttonBox.add(userMessage);

        incomingList = new JList();                                                // 会显示收到信息的组件
        incomingList.addListSelectionListener(new MyListSelectionListener());      //
        incomingList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);// 只能单选       //
        JScrollPane theList = new JScrollPane(incomingList);                        //
        buttonBox.add(theList);                                                       //
        incomingList.setListData(listVector);                                        //

        Box nameBox = new Box(BoxLayout.Y_AXIS);
        for (int i = 0; i < 16; i++) {
            nameBox.add(new Label(instrumentNames[i]));
        }

        background.add(BorderLayout.EAST,buttonBox);
        background.add(BorderLayout.WEST,nameBox);

        theFrame.getContentPane().add(background);

        GridLayout grid = new GridLayout(16,16);
        grid.setVgap(1); // 网格垂直相邻距离
        grid.setHgap(2); // 网格水平相邻距离
        mainPanel = new JPanel(grid);
        background.add(BorderLayout.CENTER,mainPanel);

        for (int i = 0; i < 256; i++){     //  设定未勾选的方框为false，则已勾选的为true，将扫描信息存在ckeckboxList中，并且发给面板
            JCheckBox c = new JCheckBox();
            c.setSelected(false);
            checkboxList.add(c);
            mainPanel.add(c);
        }

        theFrame.setBounds(50,50,300,300);
        theFrame.pack(); // 使此窗口的大小适合其子组件的首选大小和布局
        theFrame.setVisible(true);
    }

    public void setUpMidi(){
        try{
            sequencer = MidiSystem.getSequencer();
            sequencer.open();
            sequence = new Sequence(Sequence.PPQ,4);
            track = sequence.createTrack();
            sequencer.setTempoInBPM(120);
        }catch (Exception e){e.printStackTrace();}
    }

    public void buildTrackAndStart(){ //创建出16各元素的数组来存储一项乐器的值。如果该节应该要演奏，其值会是关键字，否则值为零
        ArrayList<Integer> trackList = null;
        sequence.deleteTrack(track); // 清除旧的track 做一个新的
        track = sequence.createTrack();

        for (int i = 0; i < 16; i++) { // 对每个乐器都执行一次
            trackList = new ArrayList<Integer>();

            for (int j = 0; j < 16; j++) { //对每一拍执行一次
                JCheckBox jc = (JCheckBox) checkboxList.get(j + (16 * i));
                if (jc.isSelected()) {                       // 如果有勾选，将关键字值放到数组的该位置上，不然的话就补零
                    int key = instruments[i];
                    trackList.add(new Integer(key));
                } else {
                    trackList.add(null);
                }
            }
            makeTracks(trackList); // 内循环，某种乐器的16拍，外循环，切换16种乐器；tracklist分别的到每种乐器的16拍信息后用maketracks制作需要播放的专辑
        }

        track.add(makeEvent(192,9,1,0,15));// 确保第16拍有事件，否则beatbox不会重复播放
        try{
            sequencer.setSequence(sequence);
            sequencer.setLoopCount(sequencer.LOOP_CONTINUOUSLY); // 指定无穷的重复次数
            sequencer.start();
            sequencer.setTempoInBPM(120);                       // 开始播放
        }catch (Exception e){e.printStackTrace();}
    }

    // 多个监听的内部类
    public class MyStartListener implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent a){
            buildTrackAndStart();
        }
    }

    public class MyStopListener implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent a){
            sequencer.stop();
        }
    }

    public class MyUpTempoListener implements ActionListener{     //节奏加快
        @Override
        public void actionPerformed(ActionEvent a){
            float tempoFactor = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float)(tempoFactor*1.03));
        }
    }

    public class MyDownTempoListener implements ActionListener{     // 节奏减慢
        @Override
        public void actionPerformed(ActionEvent a){
            float tempoFactor = sequencer.getTempoFactor();
            sequencer.setTempoFactor((float)(tempoFactor*0.97));
        }
    }

    public class MySendListener implements ActionListener{
        @Override
        public void actionPerformed(ActionEvent e) {
            boolean[] checkboxState = new boolean[256];
            for (int i = 0; i < 256; i++){
                JCheckBox check = (JCheckBox) checkboxList.get(i);
                if (check.isSelected()){
                    checkboxState[i] = true;
                }
            }
            try{
                out.writeObject(userName + nextNum++ + ":" + userMessage);
                out.writeObject(checkboxState);
            }catch (Exception ex){
                System.out.println("Sorry dude. Could not send it to the server.");
            }
            userMessage.setText("");
        }
    }

//    public class MyReadInListener implements ActionListener{
//        @Override
//        public void actionPerformed(ActionEvent e) {
//            boolean[] checkboxState = null;
//            try{
//                FileInputStream fileIn = new FileInputStream(new File("Checkbox.ser"));
//                ObjectInputStream is = new ObjectInputStream(fileIn);
//                checkboxState = (boolean[]) is.readObject();
//            }catch (Exception ex){
//                ex.printStackTrace();
//            }
//
//            for ( int i = 0; i < 256; i++){
//                JCheckBox check = (JCheckBox) checkboxList.get(i);
//                if (checkboxState[i]){
//                    check.setSelected(true);
//                }else {
//                    check.setSelected(false);
//                }
//            }
//            sequencer.stop();
//            buildTrackAndStart();
//        }
//    }

    public class MyListSelectionListener implements ListSelectionListener{
        public void valueChanged(ListSelectionEvent le){
            if (!le.getValueIsAdjusting()){
                String selected = (String) incomingList.getSelectedValue();
                if (selected != null){
                    boolean[] selectedState = (boolean[]) otherSeqsMap.get(selected);
                    changeSequence(selectedState);
                    sequencer.stop();
                    buildTrackAndStart();
                }
            }
        }
    }

    public class RemoteReader implements Runnable{
        boolean[] checkboxState = null;
        String nameToShow = null;
        Object obj = null;;
        public void run (){
            try {
                while ((obj = in.readObject()) != null) {
                    System.out.println("got an object from server");
                    System.out.println(obj.getClass());
                    checkboxState = (boolean[]) in.readObject();
                    otherSeqsMap.put(nameToShow, checkboxState);
                    listVector.add(nameToShow);
                    incomingList.setListData(listVector);
                }
            }catch (Exception ex){ex.printStackTrace();}
        }
    }

    public class MyPlayMineListener implements ActionListener{
        public void actionPerformed(ActionEvent a){
            if (mySequence != null){
                sequence = mySequence;
            }
        }
    }

    public void changeSequence(boolean[] chechboxState){
        for (int i = 0; i < 256; i++){
            JCheckBox check = (JCheckBox)checkboxList.get(i);
            if (chechboxState[i]){
                check.setSelected(true);
            }else {
                check.setSelected(false);
            }
        }
    }

    public void makeTracks(ArrayList list){
        Iterator it = list.iterator();
        for (int i = 0; i < 16; i++){
            Integer num = (Integer)it.next();
            if (num != null){
                int numKey = num.intValue();  // 将 Integer的值作为 int
                track.add(makeEvent(144,9,numKey,100,i));
                track.add(makeEvent(128,9,numKey,100,i+1));
            }
        }
    }

    public MidiEvent makeEvent(int comd, int chan, int one, int two, int tick){
        MidiEvent event = null;
        try{
            ShortMessage a = new ShortMessage();
            a.setMessage(comd,chan,one,two);
            event = new MidiEvent(a,tick);
        }catch (Exception e){e.printStackTrace();}
        return event;
    }
}
