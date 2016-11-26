package biz.myworkstyle.fruitsparadise;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.util.Arrays;


// イベント契機はは３つ
//  thread , touch , onDraw

public class GameManager extends View
        implements Runnable{

    private static final String LOGID ="IKIBITO";

    private MainActivity callbackClass;


    private Thread thread;
    private boolean threadOn;

    private int gamemode;//appの状態のメインの切り替え変数
    private int modestate;//各モードのサブ状態(任意)

    //ステージの仮想フィールド
    private int[][] stageField    // -1:VOID
            ,stageFieldStatus    // 0: NORMAL , 1:SWAP , 2:DELETE , 3:DOWN
            , stageFieldAnimeTimeStatus  //主にアニメ用時間として  n -> 0 に変化 （0:なし)
            ,stageFieldSwapDirStatus  //アニメの方向保持用 (ref  SWAP_DIR_??? : RIGHT ,DOWN)
            , investigatedWorkField, sameSpotThisTimeInWorkField; //チェック用テンポラリ
    private int[] gumiColor;//from 0～  : 空は -1

    private  static final int FIELD_VOID = -1 , BLOCK_NORMAL = 0, BLOCK_SWAPPING = 1,  BLOCK_DELETING = 2 , BLOCK_GOINGDOWN = 3;
    private  static final int MODE_INIT = 0 , MODE_TITLE = 1 , MODE_GAME = 2;
    private  static final int BLOCK_CHECK_NONE = 0 , BLOCK_CHECK_MARK = 1;


    private Paint painter;
    private GameManager spriteCanvas;

    //first catch item index
    private int catchItemX , catchItemY;
    private boolean catchFlag;

    //next catch index
    private boolean nextItemFlag;
    private int nextItemX , nextItemY;

    //temporary catch index
    private boolean pointItemFlag;
    private int pointItemX , pointItemY;

    //未使用
    private boolean fevertime;
    private int ferverNum;

    //animation 時間
    private int swapAnimTime = 6 , downAnimTime =4, delAnimTime = 32;
    //private int swapAnimTime = 15 , downAnimTime =20, delAnimTime = 35;

    private int score;
    private int time;

    //field
    private int fieldSizeX = 7, fieldSizeY = 10;//ブロック数
    private int fieldX = 70,fieldY = 200;       //フィールドの左上の位置
    private int blockSpaceX = 90 , blockSpaceY = 90; // 1ブロックサイズ

    //xmlで呼ばれるとき
    public GameManager(Context context, AttributeSet attrs) {
        super(context, attrs);
        setFocusable(true);

    }

    /*public SpriteView(Context context) {
        super(context);
    }*/

    //call from main activity
    void onResume(){

        try{
            //初期化
            gamemode = MODE_INIT;
            stageField = new int[fieldSizeY][fieldSizeX];
            stageFieldStatus = new int[fieldSizeY][fieldSizeX];;
            stageFieldAnimeTimeStatus = new int[fieldSizeY][fieldSizeX];;
            stageFieldSwapDirStatus = new int[fieldSizeY][fieldSizeX];;
            investigatedWorkField = new int[fieldSizeY][fieldSizeX];;
            sameSpotThisTimeInWorkField = new int[fieldSizeY][fieldSizeX];;

            gumiColor = new int[]{Color.RED, Color.BLUE, Color.YELLOW
                    , Color.MAGENTA/*紫 FF00FF*/, Color.GREEN, Color.CYAN/*水色 00FFFF*/};

            painter = new Paint();
            painter.setTextSize(50);
            painter.setAntiAlias(true);
            painter.setTypeface(Typeface.DEFAULT_BOLD);


        }catch (Exception e){
            Log.d(LOGID, Log.getStackTraceString(e));
        }
    }

    //call from main activity
    void windowChanged() {
        calcBlockSize();
    }

    private void calcBlockSize(){
        //calc block size
        int w = getWidth();
        int h = getHeight();
        int bx;
        blockSpaceX = (w - (fieldX<<1))/ fieldSizeX;
        blockSpaceY = (h - (fieldY+15))/ fieldSizeY;

        bx = Math.min(blockSpaceX,blockSpaceY);
        if (bx != blockSpaceX) {
            blockSpaceX = bx;
            fieldX = (w - blockSpaceX * fieldSizeX) >> 1;//2で割る
        }else{
            blockSpaceY = bx;
        }

        Log.d(LOGID, "window:" + w +","+h + "  block:"+ blockSpaceX + ","+blockSpaceY + "  fx:"+fieldSizeX);
    }

    void setDrawCallBack(MainActivity callClass){
        callbackClass = callClass;
    }

    //----------------------------------------------------

    // thread -> game/update  -> onDraw ->  gameDraw -> gamedraw

    public void run(){
        Log.d(LOGID, "Thread start");
        try {
            Thread.sleep(30);//useless

            //エラー落ちしても、キャッチできるよう2重ループ(here and mainloop())
            while (thread == Thread.currentThread() && threadOn){
                mainLoop();

                //ここはあまり通らない
            }
        } catch (Exception e) {
            Log.d(LOGID, Log.getStackTraceString(e));
        }
        Log.d(LOGID, "thread end origin: " + threadOn + " ," +(thread == Thread.currentThread()));
    }

    private void mainLoop(){
        //高速化のため、tryはloop外
        try {
            while (thread == Thread.currentThread() && threadOn){
                switch (gamemode){
                    case MODE_INIT:
                    case MODE_TITLE:
                        title();
                        break;
                    case MODE_GAME:
                        game();
                        break;
                }

                update();//間接的に描画処理を呼ぶ
                Thread.sleep(25);
            }
        } catch (Exception e) {
            Log.d(LOGID, Log.getStackTraceString(e));
        }
        Log.d(LOGID, "thread end 0");
    }


    private void title(){
        //touchイベントでgamemode 移動

    }

    //call from touch event
    private void setGameOn(){
        gamemode = 2;
        modestate = 0;
        score = 0;
        time = 1000;
        for (int j = 0; j < fieldSizeY; j++) {
            for (int i = 0; i < fieldSizeX; i++) {
                //stageField[j][i] = 0;
                stageField[j][i] = getRandomItem();
                stageFieldStatus[j][i] = BLOCK_NORMAL ;// NOTHING;

                stageFieldAnimeTimeStatus = new int[fieldSizeY][fieldSizeX];
                stageFieldSwapDirStatus = new int[fieldSizeY][fieldSizeX];

                //checkDelItemで都度対応
                //investigatedWorkField,sameSpotThisTimeInWorkField
            }
        }

        if (thread == null) {
            thread = new Thread(this);
            threadOn = true;
            thread.start();
        }
        Log.d(LOGID, "game on!");
    }

    private void erase4BlocksBeforeGame(){
        downendAfterTime = 4;//4つ並んだものを消しておく
        boolean allStatic = false;
        try {
            while (!allStatic){
                checkDeletabeItem();
                allStatic = true;
                animationCheck();
                for (int j = 0; j < fieldSizeY; j++) {
                    for (int i = 0; i < fieldSizeX; i++) {
                        if (!isStatic(i,j))
                            allStatic = false;
                        stageFieldAnimeTimeStatus[j][i] = -100;//すぐ消えるように適当
                    }
                }
                score = 0;//temp
                update();
                //Thread.sleep(100);
            }
        }catch (Exception e){
            Log.d(LOGID, Log.getStackTraceString(e));
        }
        Log.d(LOGID, "before check end.");
    }

    //game中メインループで動くところ
    private void game(){
        //make field
        if (modestate == 0) {
            erase4BlocksBeforeGame();
            modestate = 1;
            score = 0;
            fevertime = false;
            ferverNum =0;
        }else {
            time--;//未実装
            animationCheck();

            //未実装
            if (!fevertime)
                ferverNum = 0;
        }
    }

    //fieldにはすでに動作後の値は設定しておき、animationで後追いさせる
    private void animationCheck(){
        boolean checkfevertime = false;
        boolean swapend = false;
        boolean downend = false;
        for (int j = fieldSizeY-1; j >= 0; j--) {
            for (int i = 0; i < fieldSizeX; i++) {
                //normal
                if (stageFieldStatus[j][i] == BLOCK_NORMAL) {
                    //静止しても一定時間待つ状態を確認できるように
                    stageFieldAnimeTimeStatus[j][i]--;
                    if (stageFieldAnimeTimeStatus[j][i] < -100)//適当
                        stageFieldAnimeTimeStatus[j][i] = -100;
                } else {
                    stageFieldAnimeTimeStatus[j][i]--;
                    if (stageFieldAnimeTimeStatus[j][i] <= 0) {//アニメ完了時
                        //swap
                        if (stageFieldStatus[j][i] == BLOCK_SWAPPING) {
                            swapend = true;
                            //itemは入れ替え済み by swapItem()
                            stageFieldStatus[j][i] = BLOCK_NORMAL;
                            stageFieldAnimeTimeStatus[j][i] = -100;
                        }
                        //delete
                        else if (stageFieldStatus[j][i] == BLOCK_DELETING) {
                            //周りが消せそうなら消す。
                            checkDeletedNeighborItem(i, j);

                            setDelAndDown(i, j);//上から落ちてくるので 空設定を上書きしない
                            checkfevertime = true;
                        }
                        //down
                        else if (stageFieldStatus[j][i] == BLOCK_GOINGDOWN) {
                            //itemは落とし済み by SetDown()
                            if (checkGoingDown(i, j)) {
                                downend = true;//downが一旦終わった。落下が続くことはない。
                                checkDeletingObjectToNext(i, j);
                            }
                            stageFieldStatus[j][i] = BLOCK_NORMAL;
                        }
                        //anime 未完
                    } else {
                        if (stageFieldStatus[j][i] == BLOCK_DELETING) {
                            //処理が重いのでたまに
                            if (stageFieldAnimeTimeStatus[j][i] % 5 == 3) {
                                //周りが消せそうなら消す。
                                checkDeletedNeighborItem(i, j);
                            }
                        }
                    }
                }

                if (isVoidField(i,j)){
                    isDowningAbove(i,j);
                    //落下開始できるか？
                }
            }
        }

        //後処理関係
        fevertime = checkfevertime;

        if (downendAfterTime >= 0)// 落ちた直後はチェックせず、少し待つのでしばらくはチェックする
            checkDeletabeItem();//入れ替えた後削除できる？  //毎回チェックすると襲い
        downendAfterTime--;
        if (swapend || downend)
            downendAfterTime = 4;//上のそのしばらくの値。isActive()よりもゆとりを持たせること。
    }
    private int downendAfterTime;

    //空になった x, yに値(上から)入れ、アニメーション設定をする。
    //その上も同様に空にする、上がなかったら新規
    private void setDelAndDown(int x, int y){
        stageField[y][x] = FIELD_VOID;//-1
        stageFieldStatus[y][x] = BLOCK_NORMAL;//念のため

        if (y >0) {
            if (isStatic(x,y-1) && !isVoidField(x,y-1)) {
                setDown(x,y,stageField[y - 1][x]);
                setDelAndDown(x, y - 1);
            }
        }else {
            setDown(x,0,getRandomItem());
        }
    }

    //落ち切ってないなら続ける。
    //@return 落ち続けず、落ち切ったので消えるかチェックしろ:true
    private boolean checkGoingDown(int x, int y){
        if (y+1<fieldSizeY){
            if (isLowerVoidField(x,y)){
                setDownFrom(x,y);
                return false;
            }
        }
        return true;
    }

    /*private boolean underAllBury(int x, int y){
        for (int j = y+1 ; j< fieldSizeY;j++){
            if (!(stageField[j][x] != FIELD_VOID && stageFieldStatus[j][x] == BLOCK_NORMAL))
                return false;
        }
        return true;
    }*/
    private void checkDeletingObjectToNext(int x,int y){
        isSameDeleting(x,y,1,0);
        isSameDeleting(x,y,-1,0);
        isSameDeleting(x,y,0,-1);
    }
    private void isSameDeleting(int x, int y , int dx, int dy){
        if (beBoundArea(x+dx,y+dy) && stageField[y][x] == stageField[y+dy][x+dx]
                && stageFieldStatus[y+dy][x+dx] == BLOCK_DELETING){
            stageFieldStatus[y][x] = BLOCK_DELETING;//erase item
            stageFieldAnimeTimeStatus[y][x] = stageFieldAnimeTimeStatus[y+dy][x+dx];
            score +=10;
        }
    }
    private void checkDeletedNeighborItem(int x, int y){
        //x,yは今消した状態.ギリギリ ciを持っている前提。
        isSameStaticCatchUpDeleting(x,y,1,0);
        isSameStaticCatchUpDeleting(x,y,-1,0);
        isSameStaticCatchUpDeleting(x,y,0,1);
        isSameStaticCatchUpDeleting(x,y,0,-1);
    }

    private void isSameStaticCatchUpDeleting(int x, int y , int dx, int dy){
        if (beBoundArea(x+dx,y+dy) && stageField[y][x] == stageField[y+dy][x+dx]
                && stageFieldStatus[y+dy][x+dx] == BLOCK_NORMAL){
            stageFieldStatus[y+dy][x+dx] = BLOCK_DELETING;//erase item
            stageFieldAnimeTimeStatus[y+dy][x+dx] = Math.max(delAnimTime>>1,stageFieldAnimeTimeStatus[y][x]);
            score +=10;
        }
    }

    private boolean isDowningAbove(int x, int y){
        if (y-1>=0){
            if (!isActive(x,y-1)){
                setDownFrom(x,y-1);
                return false;
            }
        }else{
            setDelAndDown(x,0);//冗長
        }
        return true;
    }

    //toは 空な時だけ呼ぶ
    private void setDownFrom(int x, int y){
        setDown(x,y+1,stageField[y][x]);
        stageField[y][x] = FIELD_VOID;
    }

    private void setDown(int x, int y, int id){
        stageField[y][x] = id;
        stageFieldStatus[y][x] = BLOCK_GOINGDOWN;
        stageFieldAnimeTimeStatus[y][x] = downAnimTime;
    }


    private int debugVisible = 0;
    private int cc;

    private void checkDeletabeItem(){
        //Log.d(LOGID, "check delete item.");
        for (int j = 0; j < fieldSizeY; j++) {
            Arrays.fill(investigatedWorkField[j],0);
            Arrays.fill(sameSpotThisTimeInWorkField[j],0);
        }

        //消えるところをマーク  status[][] =2;
        int cci;
        int doujikeshi = 0;
        boolean deleted = false;
        for (int j = 0; j < fieldSizeY; j++) {
            for (int i = 0; i < fieldSizeX; i++) {
                if (investigatedWorkField[j][i] != 0){
                    continue;
                }
                //1 item
                ///右
                cci = stageField[j][i];//current color index
                cc = 1;//same color count
                //隣り合う同じ色を再帰的に右/下でマーク
                investigatedWorkField[j][i] = 1;  // 0空 or 1 チェック済み 同じことをn回しないように
                sameSpotThisTimeInWorkField[j][i] = 1; // 0空 or 1 同じ色 消すポイントをすぐ見つけられるように。 at deleteMarckedSpot
                checkSameColorPointToVanish(i, j, cci);//前提：i,jは同じ色(チェック済みOK)
                /*try{
                    debugVisible = 1;
                    update();
                    Thread.sleep(300);

                    debugVisible = 2;
                    update();
                    Thread.sleep(300);

                    debugVisible = 0;
                    update();
                    Thread.sleep(300);
                }catch (Exception e){
                    Log.d(LOGID, Log.getStackTraceString(e));
                }*/
                if (cc >= 4) {//4つ以上くっついた
                    //Log.d(LOGID, "del" + i+","+j + "("+cc+")");
                    //消せるところをマーク
                    deleteMarkedSpotActually(cci);
                    //scoreを計算
                    score += cc*(1+doujikeshi)*(1+ferverNum)*10;
                    doujikeshi++;
                    time += doujikeshi * 10;
                    //arraylist

                    deleted = true;
                }else{
                    //消せなかった場合もマークを外す。ざっくりと
                    for (int jj = 0; jj < fieldSizeY; jj++) {
                        Arrays.fill(sameSpotThisTimeInWorkField[jj],0);
                    }
                }
            }
        }

        //消えるところstatus[][] =2;
        if (deleted){
            ferverNum++;
        }
    }

    //再帰的に隣をチェックする。
    private void checkSameColorPointToVanish(int x, int y, int cci){
        checkSameColorPoint(x+1,y,cci);//右
        checkSameColorPoint(x-1,y,cci);//左
        checkSameColorPoint(x,y+1,cci);//下
        checkSameColorPoint(x,y-1,cci);//上
        //Log.d(LOGID, "check down:" + x +","+ y + " : " +isSameRight(x,y,cci) + "  " + cci +" = " + stageField[y+1][x]);
    }

    private void checkSameColorPoint(int x, int y, int id){
        if (isSameBlock(x,y,id)){
            investigatedWorkField[y][x] = BLOCK_CHECK_MARK;
            sameSpotThisTimeInWorkField[y][x] = BLOCK_CHECK_MARK;
            cc++;
            checkSameColorPointToVanish(x,y,id);
        }
    }
    private boolean isSameBlock(int x, int y, int id){
        if (!beBoundArea(x,y)){
            return false;
        }
        if (investigatedWorkField[y][x] != BLOCK_CHECK_NONE
                || isActive(x,y)){
            //チェック済み
            return false;
        }
        return (stageField[y][x] == id);
    }

    private void deleteMarkedSpotActually(int cci){
        int cn =  cc;
        for (int j = 0; j < fieldSizeY; j++) {
            for (int i = 0; i < fieldSizeX; i++) {
                if (sameSpotThisTimeInWorkField[j][i] == 1) {
                    stageFieldStatus[j][i] = BLOCK_DELETING;//erase item
                    sameSpotThisTimeInWorkField[j][i] = BLOCK_CHECK_NONE;//4つ以上の場所のマークを元に戻しておく
                    stageFieldAnimeTimeStatus[j][i] = delAnimTime;//アニメ用
                    cn--;
                    if (cn == 0)
                        return;
                }
            }
        }
    }

    private boolean isVoidField(int x, int y){
        return (stageField[y][x] == FIELD_VOID);
    }
    private boolean isLowerVoidField(int x, int y){
        if (y+1 >= fieldSizeY)
            return false;
        return (isVoidField(x,y+1));
    }
    private boolean isActive(int x, int y){
        //stageFieldStatus[y][x] != BLOCK_NORMAL
        return !isStatic(x,y);
    }
    private boolean isStatic(int x, int y){
        return ((stageFieldStatus[y][x] == BLOCK_NORMAL ||stageField[y][x] == FIELD_VOID)
                && stageFieldAnimeTimeStatus[y][x] < -1);//少しくらい落ち着いてから
    }
    //-----------------------------------------------------------
    //[PAINT]

    private static final int MSG_MAIN_UPDATE = 1;
    private Handler handler = new Handler() {
        public void handleMessage(Message msg) {
            try {
                if (msg.what == MSG_MAIN_UPDATE) {
                    invalidate();//invalidate で間接的に 更新を依頼
                }
            } catch (Exception e) {
                Log.d(LOGID, Log.getStackTraceString(e));
            }
        }
    };

    private void update(){
        //paintはmain threadで行う
        Message msg = Message.obtain();
        msg.what = MSG_MAIN_UPDATE;
        handler.sendMessage(msg);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        // 背景、
        canvas.drawColor(Color.LTGRAY);//Color.argb(125, 0, 0, 255));半透明

        try {
                switch (gamemode) {
                    case MODE_INIT:
                    case MODE_TITLE:
                        drawTitle(canvas);
                        break;
                    case MODE_GAME:
                        drawGame(canvas);
                        break;
                }
        }catch (Exception e){
            Log.d(LOGID, Log.getStackTraceString(e));
        }
    }

    private void drawTitle(Canvas canvas){
        painter.setTextSize(100);
        canvas.drawText("Title  touch to start!", 120, 550, painter);
    }

    private void drawGame(Canvas canvas){
        if (modestate != 0)
            drawField(canvas);
        //drawEffect();
        drawInfo(canvas);
    }

    private void drawField(Canvas canvas){
        int bx , by;
        int sx , sy;

        bx = fieldX;
        by = fieldY;
        sx = blockSpaceX;
        sy = blockSpaceY;

        int x, y,ci = 0;
        boolean blink = false;

        painter.setTextSize(20);

        for (int j = 0; j < fieldSizeY; j++) {
            for (int i = 0; i < fieldSizeX; i++) {
                if (isVoidField(i,j))//ciチェック
                    continue;
                x = bx + i * sx + 3;
                y = by + j * sy + 3;
                if (debugVisible == 0) {
                    //通常時
                    ci = stageField[j][i];
                    if (ci == -1)//なぜかOutOfBondが連発する
                        continue;
                    ci = gumiColor[ci];
                    switch (stageFieldStatus[j][i]) {
                        case BLOCK_NORMAL:
                            break;
                        case BLOCK_SWAPPING:// 1右, 4左 2下 3上
                            switch (stageFieldSwapDirStatus[j][i]) {
                                case SWAP_DIR_RIGHT:
                                    x += sx * stageFieldAnimeTimeStatus[j][i] / swapAnimTime;
                                    break;
                                case SWAP_DIR_LEFT:
                                    x -= sx * stageFieldAnimeTimeStatus[j][i] / swapAnimTime;
                                    break;
                                case SWAP_DIR_DOWN:
                                    y += sy * stageFieldAnimeTimeStatus[j][i] / swapAnimTime;
                                    break;
                                case SWAP_DIR_UP:
                                    y -= sy * stageFieldAnimeTimeStatus[j][i] / swapAnimTime;
                                    break;
                            }
                            break;
                        case BLOCK_DELETING:
                            blink = (stageFieldAnimeTimeStatus[j][i] / 2) % 5 % 2 != 0;//適当
                            break;
                        case BLOCK_GOINGDOWN:
                            y -= sy * stageFieldAnimeTimeStatus[j][i] / downAnimTime;
                            break;
                    }

                    //for debug
                }else if (debugVisible == 1){
                    ci = (investigatedWorkField[j][i] == 0)?Color.WHITE:Color.RED;

                }else if (debugVisible == 2){
                    ci = (sameSpotThisTimeInWorkField[j][i] == 0)?Color.WHITE:Color.BLUE;
                }
                painter.setColor(ci);
                if (!blink) {
                    canvas.drawRect(x, y, x + sx - 6, y + sy - 6, painter);
                }
                if (false) {//debug
                    painter.setColor(Color.WHITE);
                    canvas.drawText(""+stageFieldStatus[j][i],x,y,painter);
                    canvas.drawText(""+stageFieldAnimeTimeStatus[j][i],x+10,y,painter);
                }
                blink = false;
            }
        }
    }

    private void drawInfo(Canvas canvas){
        painter.setColor(Color.BLACK);
        painter.setTextSize(60);
        canvas.drawText("Score:"+score, 450, 150, painter);

        if (modestate == 0) {
            if ((System.currentTimeMillis()/100 % 30) % 3 != 0)
                canvas.drawText("wait...", 200, 500, painter);
        }

    }

    //----------------------------------
    //[EVENT]

    @Override
    public boolean onTouchEvent(MotionEvent event) {

        //Log.d(LOGID, "on touch in GM");

        //仮
        if (gamemode == MODE_INIT || gamemode == MODE_TITLE ){
            setGameOn();
            return true;
        }

        //gamemode: 2
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                catchFlag = false;
                nextItemFlag = false;//違う場所でも最後にそれらしいものを対象とするため、この段階で消す(UP/MOVEの都度ではなく)
                checkCatchingItem((int)event.getX(),(int)event.getY(),true);
                if (catchFlag)
                    cathingItem();
                break;
            case MotionEvent.ACTION_UP:
                if (catchFlag) {
                    checkCatchingItem((int) event.getX(), (int) event.getY(), false);
                    checkNextItem();
                    if (nextItemFlag) {//違う場所でも最後にそれらしいものを対象とする。
                        swapItem();
                        //checkDeletabeItem(); スワップアニメーションが終わってから
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (catchFlag) {
                    checkCatchingItem((int) event.getX(), (int) event.getY(), false);
                    checkNextItem();//とりあえずつかんでおく
                }
                break;
            case MotionEvent.ACTION_CANCEL:
                break;
        }

        return true;
    }

    //------------------------------------
    // sub functions

    private void checkCatchingItem(int tx, int ty, boolean first){
        tx -= fieldX;
        ty -= fieldY;

        int cx , cy;
        cx = tx / blockSpaceX;
        cy = ty / blockSpaceY;
        pointItemFlag = false;

        if (beBoundArea(cx,cy) && !isActive(cx,cy)){
            if (first) {
                //touch downで最初に選択したもの
                catchFlag = true;
                catchItemX = cx;
                catchItemY = cy;
            }else{
                //最後に操作したもの (touch down時を除く)
                pointItemFlag = true;
                pointItemX = cx;
                pointItemY = cy;
            }
        }
    }

    private void cathingItem(){
        //play sound..
    }
    private void memoryMatchedNextItem(){
        //touchdown の隣のもの
        nextItemFlag = true;
        nextItemX = pointItemX;
        nextItemY = pointItemY;
    }

    private void checkNextItem(){
        if (catchFlag && pointItemFlag){
            if (catchItemX == pointItemX){
                if (catchItemY +1 == pointItemY
                        || catchItemY -1 == pointItemY){
                    memoryMatchedNextItem();
                }
            }else
            if (catchItemY == pointItemY){
                if (catchItemX +1 == pointItemX
                        || catchItemX -1 == pointItemX){
                    memoryMatchedNextItem();
                }
            }
        }
    }

    private void swapItem(){
        //todo animation
        //lock

        int x = catchItemX;
        int y = catchItemY;

        int c = stageField[y][x];
        stageField[y][x] = stageField[nextItemY][nextItemX];
        stageField[nextItemY][nextItemX] = c;

        //swap lock
        stageFieldStatus[y][x] = stageFieldStatus[nextItemY][nextItemX] = 1;
        stageFieldAnimeTimeStatus[y][x] = stageFieldAnimeTimeStatus[nextItemY][nextItemX] = swapAnimTime;//swap anime(0に向けて減らす)

        //どこからスワップするかの方向を入れる  // 1右, 4左 2下 3上
        int d = getSwapDir(x,y,nextItemX,nextItemY);
        stageFieldSwapDirStatus[y][x] = d;
        stageFieldSwapDirStatus[nextItemY][nextItemX] = 5-d;
    }

    private  final static int SWAP_DIR_RIGHT = 1, SWAP_DIR_LEFT = (5-SWAP_DIR_RIGHT)
            ,SWAP_DIR_DOWN = 2, SWAP_DIR_UP = (5-SWAP_DIR_DOWN);

    //どこからスワップするかの方向を入れる  // 1右, 4左 2下 3上  //相補性があるから
    private int getSwapDir(int ox, int oy , int fx , int fy){
        if (ox == fx){
            return (oy < fy?SWAP_DIR_DOWN:SWAP_DIR_UP);
        }else{
            return (ox < fx?SWAP_DIR_RIGHT:SWAP_DIR_LEFT);
        }
    }

    private boolean beBoundArea(int x, int y){
        return (x>=0 && x < fieldSizeX && y >= 0 && y < fieldSizeY);
    }

    private int getRandomItem(){
        return (int)(Math.random()*6);
    }

    //------------------------------------
    // mini func



}
