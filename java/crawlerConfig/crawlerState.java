package crawlerConfig;

/**
 * Created by DALAB1 on 2018-04-23.
 */
public class crawlerState {
    private itemConfig item;
    private String startDate;
    private String endDate;

    private String dataType;
    private int currCount;
    private int breakCount = 100;

    private int breakRest;
    private int blockedRest;

    private int totalPage;
    private int currPage;

    public crawlerState(itemConfig item, String dataType, String startDate, String endDate){
        this.item = item;
        this.dataType = dataType;

        this.currCount = 0;

        this.totalPage = 0;
        this.currPage = 0;
        this.breakRest = 20000;
        this.blockedRest = 30000;

        this.startDate = startDate;
        this.endDate = endDate;
    }

    public itemConfig getItem(){
        return this.item;
    }
    public String getStartDate(){
        return this.startDate;
    }
    public String getEndDate(){
        return this.endDate;
    }
    public int getTotalPage(){
        return this.totalPage;
    }
    public int getCurrPage(){
        return this.currPage;
    }
    public int getBreakCount(){
        return this.breakCount;
    }
    public int getCurrCount(){
        return this.currCount;
    }
    public int getBreakRest(){
        return this.breakRest;
    }
    public int getBlockedRest(){
        return this.blockedRest;
    }
    public void setItem(itemConfig item){
        this.item = item;
    }
    public void setStartDate(String startDate){
        this.startDate = startDate;
    }
    public void setEndDate(String endDate){
        this.endDate = endDate;
    }
    public void setTotalPage(int totalPage){
        this.totalPage = totalPage;
    }
    public void setCurrPage(int currPage){
        this.currPage = currPage;
    }
    public void setCurrCount(int currCount){
        this.currCount = currCount;
    }
}
