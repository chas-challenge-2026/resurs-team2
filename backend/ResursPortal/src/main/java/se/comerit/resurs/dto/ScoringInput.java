package se.comerit.resurs.dto;

import java.math.BigDecimal;

public record ScoringInput(
        double egetKapital,
        double totaltKapital,
        double omsattningstillgangar,
        double kortfristigaSkulder,
        double totalaSkulder,
        double rorelseresultat,
        double nettoomsattning,
        BigDecimal requestedAmount,
        double operativtKassaflode,
        double investeringsKassaflode,
        double ranteKostnader,
        String bransch
) {


}
