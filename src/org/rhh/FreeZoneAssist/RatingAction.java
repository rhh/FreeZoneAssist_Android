package org.rhh.FreeZoneAssist;
import java.util.Date;


public class RatingAction 
{
    //class variables
    public enum Types { add1, sub1, add2, sub2, add5, sub5, add10, sub10, start, stop, fatalstop, back, timeout, init };
    public static int ActualPoints = 0;
    public static int ActualDriver = 0;
    public static int ActualSection = 0;
    public static int IDCounter = 0;

    //object variables
    public Types Type;
    public Date TimeStamp;
    public int Points;
    public int Driver;
    public int Section;
    public int ID;

    public RatingAction(Types TypeOfRatingAction)
    {
        this.Type = TypeOfRatingAction;
        this.Driver = ActualDriver;
        this.Section = ActualSection;
        this.ID = ++IDCounter;      // starts with 1!
        TimeStamp = new Date();

        switch(this.Type)
        {
            case add1:
                ActualPoints += 1;
                break;
            case add2:
                ActualPoints += 2;
                break;
            case add5:
                ActualPoints  += 5;
                break;
            case add10:
                ActualPoints  += 10;
                break;
            case sub1:
                ActualPoints -= 1;
                break;
            case sub2:
                ActualPoints -= 2;
                break;
            case sub5:
                ActualPoints -= 5;
                break;
            case sub10:
                ActualPoints -= 10;
                break;
        }
        this.Points = ActualPoints;
    }

}
