package se.comerit.resurs.rating;

import org.springframework.stereotype.Service;
import se.comerit.resurs.dto.ScoringInput;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Service
public class ScoringService {
    private final ScoringConfig config;

    private final SolidityCheck solidityCheck;

    public ScoringService(ScoringConfig config, SolidityCheck solidityCheck) {
        this.config = config;
        this.solidityCheck = solidityCheck;
    }

    public Score score(ApplicationData input) {
        StringBuilder scoringLog = new StringBuilder();
        StringBuilder decisionReason = new StringBuilder();
        int flagCount = 0;
        boolean hardReject = false;
        int kreditPoang = 100;

        double egetKapital = input.equity();
        double totaltKapital = input.totalCapital();
        double omsattningstillgangar = input.currentAssets();
        double kortfristigaSkulder = input.currentLiabilities();
        double totalaSkulder = input.totalLiabilities();
        double rorelseresultat = input.operatingIncome();
        double nettoomsattning = input.netRevenue();
        BigDecimal requestedAmount = input.requestAmount();
        double operativtKassaflode = input.operatingCashFlow();
        double investeringsKassaflode = input.investingCashFlow();
        double ranteKostnader = input.interestExpenses();
        String bransch = input.industry();

        // --- Soliditet (eget_kapital / totalt_kapital) ---

        var solidityResult = solidityCheck.evaluate(input);
        kreditPoang += solidityResult.scoreDelta();
        switch (solidityResult.status()) {
            case FLAG:
                flagCount++;
                scoringLog.append(" [FLAGGED]");
                break;
            case REJECT:
                hardReject = true;
                scoringLog.append(" [REJECTED]");
                break;
            case OK:
                scoringLog.append(" [OK]");
                break;
            default:
                break;
        }
        decisionReason.append(solidityResult.message());

        final double soliditet = totaltKapital != 0 ? egetKapital / totaltKapital : 0.0;

        // Extra soliditet-kontroll med ANNAN tröskel (0.30) — inkonsekvent med ovan
        if (soliditet < 0.3 && requestedAmount.compareTo(new BigDecimal("1000000")) > 0) {
            flagCount++;
            decisionReason.append("VARNING: Stor kreditbelopp med soliditet under " + 0.3
                    + " – extra granskning rekommenderas. ");
            scoringLog.append(", storkredit_soliditet [FLAGGED]");
            kreditPoang -= 12;
        }

        // --- Likviditetsgrad (omsättningstillgångar / kortfristiga_skulder) ---
        scoringLog.append(", ");
        double likviditetsgrad = kortfristigaSkulder != 0 ? omsattningstillgangar / kortfristigaSkulder : 0.0;
        scoringLog.append("likviditetsgrad=").append(String.format("%.2f", likviditetsgrad));
        final double likviditetsRejectThreshold = 1.0;
        final double likviditetsFlagThreshold = 2.0;

        if (likviditetsgrad < likviditetsRejectThreshold) {
            flagCount++;
            decisionReason.append("VARNING: Likviditetsgrad under " + likviditetsRejectThreshold + " (")
                    .append(String.format("%.2f", likviditetsgrad))
                    .append("). Kortfristiga skulder överstiger omsättningstillgångar. ");
            scoringLog.append(" [FLAGGED]");
            kreditPoang -= 15;
        } else if (likviditetsgrad >= likviditetsFlagThreshold) {
            decisionReason.append("Likviditetsgrad god (").append(String.format("%.2f", likviditetsgrad)).append("). ");
            scoringLog.append(" [GOOD]");
            kreditPoang += 10;
        } else {
            decisionReason.append("Likviditetsgrad godkänd (").append(String.format("%.2f", likviditetsgrad))
                    .append("). ");
            scoringLog.append(" [OK]");
        }

        // Extra likviditets-check
        final double likviditetsCheck = 1.2;
        final double likviditetsminimumThreshold = 1.0;
        if (likviditetsgrad < likviditetsCheck && likviditetsgrad >= likviditetsminimumThreshold) {
            flagCount++;
            decisionReason.append("VARNING: Likviditetsgrad nära minimigräns (")
                    .append(String.format("%.2f", likviditetsgrad)).append(" < " + likviditetsCheck + "). ");
            scoringLog.append(", likviditet_marginal [FLAGGED]");
            kreditPoang -= 8;
        }

        // --- Skuldsättningsgrad (totala_skulder / eget_kapital) ---
        scoringLog.append(", ");
        double skuldsattningsgrad = egetKapital != 0 ? totalaSkulder / egetKapital : 0.0;
        scoringLog.append("skuldsättningsgrad=").append(String.format("%.2f", skuldsattningsgrad));
        final double debtsRejectionThreshold = 3.0;
        final double debtsWarningThreshold = 2.0;

        if (skuldsattningsgrad > debtsRejectionThreshold) {
            hardReject = true;
            decisionReason.append("AVSLAG: Skuldsättningsgrad för hög (")
                    .append(String.format("%.2f", skuldsattningsgrad))
                    .append(" > " + debtsRejectionThreshold + "). ");
            scoringLog.append(" [REJECT]");
            kreditPoang -= 35;
        } else if (skuldsattningsgrad > debtsWarningThreshold) {
            flagCount++;
            decisionReason.append("VARNING: Skuldsättningsgrad hög (").append(String.format("%.2f", skuldsattningsgrad))
                    .append(", rekommenderas under " + debtsWarningThreshold + "). ");
            scoringLog.append(" [FLAGGED]");
            kreditPoang -= 15;
        } else {
            decisionReason.append("Skuldsättningsgrad OK (").append(String.format("%.2f", skuldsattningsgrad))
                    .append("). ");
            scoringLog.append(" [OK]");
            kreditPoang += 5;
        }

        // --- Rörelseresultatmarginal (rörelseresultat / nettoomsättning) ---
        scoringLog.append(", ");
        double rorelsemarginal = nettoomsattning != 0 ? rorelseresultat / nettoomsattning : 0.0;
        scoringLog.append("rörelsemarginal=").append(String.format("%.2f", rorelsemarginal));
        final double marginalWarningThreshold = 0.02;
        final double marginalGoodThreshold = 0.10;

        if (rorelsemarginal < marginalWarningThreshold) {
            flagCount++;
            decisionReason.append("VARNING: Rörelseresultatmarginal låg (")
                    .append(String.format("%.2f", rorelsemarginal * 100))
                    .append("%, rekommenderas över " + marginalWarningThreshold + "). ");
            scoringLog.append(" [FLAGGED]");
            kreditPoang -= 10;
        } else if (rorelsemarginal > marginalGoodThreshold) {
            decisionReason.append("Rörelseresultatmarginal god (")
                    .append(String.format("%.2f", rorelsemarginal * 100))
                    .append("%, rekommenderas " + marginalGoodThreshold + "). ");
            scoringLog.append(" [GOOD]");
            kreditPoang += 8;
        } else {
            decisionReason.append("Rörelseresultatmarginal godkänd (")
                    .append(String.format("%.2f", rorelsemarginal * 100)).append("%). ");
            scoringLog.append(" [OK]");
        }

        // Kreditbeloppskontroll
        final double kreditBeloppCheck = 5000000.0;
        if (requestedAmount.compareTo(BigDecimal.valueOf(+kreditBeloppCheck)) > 0) {
            flagCount++;
            decisionReason.append(
                    "VARNING: Kreditbelopp överstiger " + kreditBeloppCheck + " kr — kräver manuell granskning. ");
            scoringLog.append(", storkredit [FLAGGED]");
            kreditPoang -= 10;
        }

        // Negativt eget kapital — ej täckt av soliditet-formeln om totalt_kapital också
        // är negativt
        final double egetKapitalThreshold = 0.0;
        if (egetKapital < egetKapitalThreshold) {
            hardReject = true;
            decisionReason.append("AVSLAG: Negativt eget kapital. ");
            scoringLog.append(", negativt_eget_kapital [REJECT]");
            kreditPoang -= 50;
        }

        // Nettoomsättning-kontroll — liten verksamhet flaggas
        final double nettoCheckThreshold = 500000.0;
        if (nettoomsattning < nettoCheckThreshold) {
            flagCount++;
            decisionReason.append("VARNING: Låg nettoomsättning (under 500 000 kr). ");
            scoringLog.append(", låg_omsättning [FLAGGED]");
            kreditPoang -= 7;
        }

        // Rörelseresultat negativt — extra flagg utöver marginalen
        final double negativtResultat = 0.0;
        if (rorelseresultat < negativtResultat) {
            flagCount++;
            decisionReason.append("VARNING: Negativt rörelseresultat. ");
            scoringLog.append(", negativt_rörelseresultat [FLAGGED]");
            kreditPoang -= 12;
        }

        // Totala skulder > nettoomsättning — inget eget threshold, bara ett av många
        // checks
        if (totalaSkulder > nettoomsattning * 2) {
            flagCount++;
            decisionReason.append("VARNING: Totala skulder överstiger dubbla nettoomsättningen. ");
            scoringLog.append(", skulder_vs_omsattning [FLAGGED]");
            kreditPoang -= 10;
        }

        // Kortfristiga skulder > omsättningstillgångar (redundant med
        // likviditetsgrad-check ovan)
        if (kortfristigaSkulder > omsattningstillgangar) {
            // Already counted in likviditetsgrad, but re-checked here — duplicate logic
            decisionReason.append("Not: Kortfristiga skulder överstiger omsättningstillgångar. ");
        }

        // ===========================================================
        // BRANSCHKORREKTIONSFAKTOR
        // Mappar branschkod till justerings-multiplikator för soliditetsgräns
        // Används BARA för ett av soliditet-checkarna nedan — inkonsekvent med övriga
        // TODO: applicera branschfaktor konsekvent på alla nyckeltal
        // ===========================================================

        double branschFaktor = config.industryFactors().getOrDefault(bransch, 1.0);

        scoringLog.append(", bransch=").append(bransch.isEmpty() ? "OKÄND" : bransch)
                .append("(faktor=").append(String.format("%.2f", branschFaktor)).append(")");

        // Branschjusterad soliditetskontroll — BARA detta check använder branschFaktor

        double branschJusteradSoliditetGrans = 0.20 * branschFaktor;
        if (soliditet < branschJusteradSoliditetGrans) {
            flagCount++;
            decisionReason.append("VARNING: Soliditet understiger branschjusterad gräns (")
                    .append(String.format("%.2f", branschJusteradSoliditetGrans))
                    .append(" för bransch ").append(bransch).append("). ");
            scoringLog.append(", bransch_soliditet [FLAGGED]");
            kreditPoang -= 8;
        }

        // HISTORISK JÄMFÖRELSE (MOCK)
        // TODO: hämta från DB — för nu hårdkodar vi branschsnitt
        // Dessa värden borde ligga i en konfigurationstabell i databasen
        Map<String, Double> branschSnittSoliditet = config.industryAverages().solidity();

        Map<String, Double> branschSnittSkuldsattning = config.industryAverages().debtRatio();

        Map<String, Double> branschSnittMarginal = config.industryAverages().margin();
        double solidityComparisonRatio = config.industryAverages().solidityComparisonRatio();
        double marginComparisnRatio = config.industryAverages().marginComparisonRatio();

        // Jämför mot branschsnitt — bara om bransch är känd
        if (branschSnittSoliditet.containsKey(bransch)) {
            double snittSoliditet = branschSnittSoliditet.get(bransch);
            if (soliditet < snittSoliditet * solidityComparisonRatio) {
                flagCount++;
                decisionReason.append("VARNING: Soliditet betydligt under branschsnitt för ")
                        .append(bransch).append(" (snitt=").append(String.format("%.2f", snittSoliditet))
                        .append("). ");
                scoringLog.append(", under_branschsnitt_soliditet [FLAGGED]");
                kreditPoang -= 6;
            }
        }

        if (branschSnittMarginal.containsKey(bransch)) {
            double snittMarginal = branschSnittMarginal.get(bransch);
            if (rorelsemarginal < snittMarginal * marginComparisnRatio) {
                flagCount++;
                decisionReason.append("VARNING: Rörelsemarginal under 50% av branschsnitt för ")
                        .append(bransch).append(". ");
                scoringLog.append(", under_branschsnitt_marginal [FLAGGED]");
                kreditPoang -= 5;
            }
        }
        // KASSAFLÖDESANALYS
        double kassaflodeKvot = totalaSkulder != 0 ? operativtKassaflode / totalaSkulder : 0.0;
        scoringLog.append(", kassaflödeskvot=").append(String.format("%.3f", kassaflodeKvot));
        double kassaflodeKvotLow = 0.05;

        if (kassaflodeKvot < 0) {
            // Negativt operativt kassaflöde — hård avvisning
            hardReject = true;
            decisionReason.append("AVSLAG: Negativt operativt kassaflöde (kassaflödeskvot=")
                    .append(String.format("%.3f", kassaflodeKvot)).append("). ");
            scoringLog.append(" [REJECT]");
            kreditPoang -= 30;
        } else if (kassaflodeKvot < kassaflodeKvotLow) {
            flagCount++;
            decisionReason.append("VARNING: Kassaflödeskvot låg (").append(String.format("%.3f", kassaflodeKvot))
                    .append(" < + ").append(kassaflodeKvotLow).append(" ). ");
            scoringLog.append(" [FLAGGED]");
            kreditPoang -= 12;
        } else {
            decisionReason.append("Kassaflödeskvot OK (").append(String.format("%.3f", kassaflodeKvot)).append("). ");
            scoringLog.append(" [OK]");
            kreditPoang += 5;
        }
        // Investeringskassaflöde — negativt är ofta normalt men flaggas ändå
        final double negativeInvestering = 0.3;
        if (investeringsKassaflode < -nettoomsattning * negativeInvestering) {
            flagCount++;
            decisionReason.append("VARNING: Högt negativt investeringskassaflöde (")
                    .append(String.format("%.0f", investeringsKassaflode)).append(" kr). ");
            scoringLog.append(", inv_kassaflode [FLAGGED]");
            kreditPoang -= 4;
        }

        // ===========================================================
        // RÄNTETÄCKNINGSGRAD (rörelseresultat / räntekostnader)
        // Edge case: negativa räntekostnader hanteras med magic number 999
        // ===========================================================

        if (ranteKostnader > 0) {
            double ranteTackningsgrad = rorelseresultat / ranteKostnader;
            scoringLog.append(", ränteTäckning=").append(String.format("%.2f", ranteTackningsgrad));
            double ranteTackningsgradRejectThreshold = 1.5;
            double ranteTackningsgradFlagThreshold = 2.5;

            if (ranteTackningsgrad < ranteTackningsgradRejectThreshold) {
                // Hard reject
                hardReject = true;
                decisionReason.append("AVSLAG: Räntetäckningsgrad under + ").append(ranteTackningsgradRejectThreshold)
                        .append(" (")
                        .append(String.format("%.2f", ranteTackningsgrad))
                        .append("). Rörelseresultat täcker ej räntekostnader. ");
                scoringLog.append(" [REJECT]");
                kreditPoang -= 35;
            } else if (ranteTackningsgrad < ranteTackningsgradFlagThreshold) {
                // Flag
                flagCount++;
                decisionReason.append("VARNING: Räntetäckningsgrad låg (")
                        .append(String.format("%.2f", ranteTackningsgrad)).append(" < +, rekommenderas minst ")
                        .append(ranteTackningsgradFlagThreshold).append("). ");
                scoringLog.append(" [FLAGGED]");
                kreditPoang -= 15;
            } else {
                decisionReason.append("Räntetäckningsgrad OK (").append(String.format("%.2f", ranteTackningsgrad))
                        .append("). ");
                scoringLog.append(" [OK]");
                kreditPoang += 8;
            }
        } else {
            decisionReason.append("Räntetäckningsgrad ej tillämplig (inga räntekostnader). ");
            scoringLog.append(" [N/A]");
        }

        // KOMBINATIONSRISKREGLER

        // Kombination 1: låg soliditet OCH hög skuldsättning — "dubbel riskindikator"
        if (soliditet < 0.2 && skuldsattningsgrad > debtsWarningThreshold) {
            flagCount++;
            decisionReason.append("VARNING: Dubbel riskindikator — låg soliditet (")
                    .append(String.format("%.2f", soliditet)).append(") kombinerat med hög skuldsättning (")
                    .append(String.format("%.2f", skuldsattningsgrad)).append("). ");
            scoringLog.append(", kombinationsrisk_soliditet_skuld [FLAGGED]");
            kreditPoang -= 18;
        }

        // Kombination 2: dålig likviditet OCH negativt rörelseresultat — omedelbar
        // avvisning
        if (likviditetsgrad < likviditetsRejectThreshold && rorelseresultat < 0) {
            hardReject = true;
            decisionReason
                    .append("AVSLAG: Kombinationsrisk — likviditetsgrad under 1.0 samt negativt rörelseresultat. ");
            scoringLog.append(", kombinationsrisk_likviditet_resultat [REJECT]");
            kreditPoang -= 40;
        }

        // Kombination 3: kreditbelopp överstiger årsoms — flaggas
        if (requestedAmount.doubleValue() > nettoomsattning) {
            flagCount++;
            decisionReason.append("VARNING: Kreditbelopp överstiger årsoms. (")
                    .append(String.format("%.0f", requestedAmount.doubleValue()))
                    .append(" kr > ").append(String.format("%.0f", nettoomsattning)).append(" kr). ");
            scoringLog.append(", kredit_vs_omsattning [FLAGGED]");
            kreditPoang -= 8;
        }

        // Kombination 4: eget kapital i förhållande till kreditbelopp
        final double kreditEgetKapitalThreshold = 0.3;
        if (requestedAmount.doubleValue() > 0
                && egetKapital / requestedAmount.doubleValue() < kreditEgetKapitalThreshold) {
            flagCount++;
            decisionReason.append("VARNING: Eget kapital täcker mindre än 30% av kreditbeloppet. ");
            scoringLog.append(", eget_kapital_vs_kredit [FLAGGED]");
            kreditPoang -= 10;
        }

        // Kombination 5: OBS — felaktig formel, borde vara (totalaSkulder /
        // nettoomsattning) men det funkar i de flesta fall
        // OBS: detta är fel, borde vara totalaSkulder / nettoomsattning men det funkar
        // i de flesta fall
        final double skuldTackningsFel = (totalaSkulder + kortfristigaSkulder) / (nettoomsattning + 1);
        if (skuldTackningsFel > debtsWarningThreshold) {
            flagCount++;
            decisionReason.append("VARNING: Skuldbörda hög relativt omsättning (kombinationscheck). ");
            scoringLog.append(", skuld_omsattning_kombination [FLAGGED]");
            kreditPoang -= 7;
        }

        // Kombination 6: kassaflöde + skuldsättning
        if (kassaflodeKvot < kassaflodeKvotLow && skuldsattningsgrad > debtsWarningThreshold) {
            flagCount++;
            decisionReason.append("VARNING: Kombinationsrisk kassaflöde + skuldsättning. ");
            scoringLog.append(", kassaflode_skuld_kombination [FLAGGED]");
            kreditPoang -= 12;
        }

        // Logga kreditpoäng i scoringLog — men poängen används INTE för beslut
        // TODO: ersätt flagCount-logiken med kreditPoang-baserad tröskel
        scoringLog.append(", kreditPoäng=").append(kreditPoang).append(" (ANVÄNDS EJ I BESLUT)");

        // ===========================================================
        // BESLUT — combine flags and hard rejects
        // ===========================================================

        String decision;
        String status;

        if (hardReject) {
            decision = "REJECTED";
            status = "REJECTED";
            decisionReason.insert(0, "=== ANSÖKAN AVSLAGEN === ");
        } else if (flagCount >= 2) {
            decision = "REVIEW";
            status = "UNDER_REVIEW";
            decisionReason.insert(0, "=== MANUELL GRANSKNING === Antal varningsflaggor: " + flagCount + ". ");
        } else if (flagCount == 1) {
            decision = "REVIEW";
            status = "UNDER_REVIEW";
            decisionReason.insert(0, "=== GRANSNING REKOMMENDERAS === 1 varningsflagga. ");
        } else {
            decision = "APPROVED";
            status = "APPROVED";
            decisionReason.insert(0, "=== ANSÖKAN GODKÄND === Alla nyckeltal uppfyller krav. ");
        }

        return new Score(decision, flagCount, scoringLog.toString(), status, decisionReason.toString());
    }
}
