package se.comerit.resurs.dto;

import java.math.BigDecimal;

public class ScoringInput {
    private double egetKapital;
    private double totaltKapital;
    private double omsattningstillgangar;
    private double kortfristigaSkulder;
    private double totalaSkulder;
    private double rorelseresultat;
    private double nettoomsattning;
    private BigDecimal requestedAmount;
    private double operativtKassaflode;
    private double investeringsKassaflode;
    private double ranteKostnader;
    private String bransch;



    // Getters & setters
    public double getEgetKapital() { return egetKapital; }
    public void setEgetKapital(double egetKapital) { this.egetKapital = egetKapital; }

    public double getTotaltKapital() { return totaltKapital; }
    public void setTotaltKapital(double totaltKapital) { this.totaltKapital = totaltKapital; }

    public double getOmsattningstillgangar() { return omsattningstillgangar; }
    public void setOmsattningstillgangar(double omsattningstillgangar) { this.omsattningstillgangar = omsattningstillgangar; }

    public double getKortfristigaSkulder() { return kortfristigaSkulder; }
    public void setKortfristigaSkulder(double kortfristigaSkulder) { this.kortfristigaSkulder = kortfristigaSkulder; }

    public double getTotalaSkulder() { return totalaSkulder; }
    public void setTotalaSkulder(double totalaSkulder) { this.totalaSkulder = totalaSkulder; }

    public double getRorelseresultat() { return rorelseresultat; }
    public void setRorelseresultat(double rorelseresultat) { this.rorelseresultat = rorelseresultat; }

    public double getNettoomsattning() { return nettoomsattning; }
    public void setNettoomsattning(double nettoomsattning) { this.nettoomsattning = nettoomsattning; }

    public BigDecimal getRequestedAmount() { return requestedAmount; }
    public void setRequestedAmount(BigDecimal requestedAmount) { this.requestedAmount = requestedAmount; }

    public double getOperativtKassaflode() { return operativtKassaflode; }
    public void setOperativtKassaflode(double operativtKassaflode) { this.operativtKassaflode = operativtKassaflode; }

    public double getInvesteringsKassaflode() { return investeringsKassaflode; }
    public void setInvesteringsKassaflode(double investeringsKassaflode) { this.investeringsKassaflode = investeringsKassaflode; }

    public double getRanteKostnader() { return ranteKostnader; }
    public void setRanteKostnader(double ranteKostnader) { this.ranteKostnader = ranteKostnader; }

    public String getBransch() { return bransch; }
    public void setBransch(String bransch) { this.bransch = bransch; }
}
