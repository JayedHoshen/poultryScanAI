package com.example.poultryscanfinal;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Single source of truth for all disease text in the app: the result screen,
 * the scan history detail screen and the disease library all read from here.
 *
 * Deliberately general husbandry and biosecurity guidance only. No medicines,
 * no dosages, no treatment protocols - those belong to a qualified poultry
 * veterinarian who has actually examined the birds.
 *
 * The keys are exactly the four classes the TFLite model can output. Nothing
 * else belongs in this map.
 */
public final class DiseaseInfo {

    /** Library display order. Same four classes as the model, nothing more. */
    public static final List<String> LIBRARY_ORDER =
            Collections.unmodifiableList(Arrays.asList("cocci", "ncd", "salmo", "healthy"));

    public final String key;
    public final String displayName;
    /** One-line summary used on the library cards. */
    public final String shortDescription;
    public final String about;
    public final String symptoms;
    public final String spread;
    public final String whatToDo;
    public final String prevention;
    public final String management;
    public final String whenToCallVet;
    public final boolean healthy;

    private DiseaseInfo(String key, String displayName, String shortDescription, String about,
                        String symptoms, String spread, String whatToDo, String prevention,
                        String management, String whenToCallVet, boolean healthy) {
        this.key = key;
        this.displayName = displayName;
        this.shortDescription = shortDescription;
        this.about = about;
        this.symptoms = symptoms;
        this.spread = spread;
        this.whatToDo = whatToDo;
        this.prevention = prevention;
        this.management = management;
        this.whenToCallVet = whenToCallVet;
        this.healthy = healthy;
    }

    private static final Map<String, DiseaseInfo> BY_LABEL;

    static {
        Map<String, DiseaseInfo> map = new HashMap<>();

        map.put("cocci", new DiseaseInfo(
                "cocci",
                "Coccidiosis",
                "Common poultry intestinal disease",
                "Coccidiosis is an intestinal disease caused by Eimeria parasites. It spreads "
                        + "through droppings and is most common in warm, damp litter. Young birds "
                        + "are usually the worst affected.",
                "\u2022 Blood-stained or unusually dark droppings\n"
                        + "\u2022 Ruffled feathers and huddling\n"
                        + "\u2022 Reduced feed and water intake\n"
                        + "\u2022 Weight loss or poor growth\n"
                        + "\u2022 Pale comb and wattles",
                "\u2022 Through droppings from infected birds\n"
                        + "\u2022 Wet or caked litter, which lets the parasite survive and multiply\n"
                        + "\u2022 Contaminated boots, equipment, feeders and drinkers\n"
                        + "\u2022 Overcrowded housing, which increases exposure",
                "\u2022 Separate visibly affected birds where practical\n"
                        + "\u2022 Replace wet or caked litter and keep it dry\n"
                        + "\u2022 Provide clean, fresh drinking water at all times\n"
                        + "\u2022 Keep feeders and drinkers off the floor and clean them daily\n"
                        + "\u2022 Contact a qualified poultry veterinarian for diagnosis and treatment",
                "\u2022 Keep litter dry and turn or replace it regularly\n"
                        + "\u2022 Avoid overcrowding\n"
                        + "\u2022 Clean and disinfect housing between flocks\n"
                        + "\u2022 Do not let drinkers leak onto the litter\n"
                        + "\u2022 Discuss a vaccination or prevention plan with your veterinarian",
                "\u2022 Keep brooding areas warm, dry and well ventilated\n"
                        + "\u2022 Watch young flocks daily during the highest-risk weeks\n"
                        + "\u2022 Stock all-in / all-out where possible instead of mixing ages\n"
                        + "\u2022 Record which houses have had outbreaks before",
                "\u2022 Blood appears in the droppings\n"
                        + "\u2022 Several birds stop eating or drinking\n"
                        + "\u2022 Growth slows across the flock\n"
                        + "\u2022 Any bird dies unexpectedly",
                false));

        map.put("ncd", new DiseaseInfo(
                "ncd",
                "Newcastle Disease",
                "Viral disease affecting poultry",
                "Newcastle Disease is a highly contagious viral disease of poultry. It spreads "
                        + "quickly through a flock and can cause heavy losses. In many countries it "
                        + "is a notifiable disease, so suspected cases must be reported to the "
                        + "authorities.",
                "\u2022 Greenish or watery droppings\n"
                        + "\u2022 Gasping, coughing or nasal discharge\n"
                        + "\u2022 Twisted neck, tremors or paralysis\n"
                        + "\u2022 Sudden drop in egg production or misshapen eggs\n"
                        + "\u2022 Swelling around the eyes and neck\n"
                        + "\u2022 Sudden deaths in the flock",
                "\u2022 Direct contact with infected birds and their droppings\n"
                        + "\u2022 Airborne droplets over short distances\n"
                        + "\u2022 Contaminated crates, vehicles, boots, clothing and equipment\n"
                        + "\u2022 Wild birds and rodents moving between houses\n"
                        + "\u2022 Newly bought birds brought in without isolation",
                "\u2022 Isolate visibly affected birds immediately\n"
                        + "\u2022 Stop moving birds, eggs and equipment on or off the farm\n"
                        + "\u2022 Restrict visitor access and change footwear between houses\n"
                        + "\u2022 Dispose of dead birds safely and promptly\n"
                        + "\u2022 Contact a qualified poultry veterinarian or your local animal "
                        + "health authority without delay",
                "\u2022 Follow a proper vaccination schedule advised by your veterinarian\n"
                        + "\u2022 Keep new or returning birds separated before mixing them in\n"
                        + "\u2022 Disinfect vehicles, crates, boots and equipment\n"
                        + "\u2022 Keep wild birds away from feed and water\n"
                        + "\u2022 Clean and disinfect housing between flocks",
                "\u2022 Keep a single entry point to the farm with a disinfection step\n"
                        + "\u2022 Keep vaccination dates written down for every batch\n"
                        + "\u2022 Do not share equipment with neighbouring farms\n"
                        + "\u2022 Keep visitor and vehicle records",
                "\u2022 Immediately, if you suspect this disease at all\n"
                        + "\u2022 Several birds show breathing or nervous signs\n"
                        + "\u2022 Deaths rise suddenly over a day or two\n"
                        + "\u2022 Egg production drops sharply across the flock",
                false));

        map.put("salmo", new DiseaseInfo(
                "salmo",
                "Salmonellosis",
                "Bacterial infection affecting poultry",
                "Salmonellosis is a bacterial infection caused by Salmonella species. It spreads "
                        + "through contaminated feed, water, litter and droppings, and some strains "
                        + "can also affect people handling the birds or their eggs.",
                "\u2022 White, pasty droppings or pasted vents\n"
                        + "\u2022 Weakness, drooping wings and huddling\n"
                        + "\u2022 Poor appetite and dehydration\n"
                        + "\u2022 Poor growth in chicks and higher chick mortality\n"
                        + "\u2022 Drop in egg production",
                "\u2022 Contaminated feed and drinking water\n"
                        + "\u2022 Droppings, dust and dirty litter\n"
                        + "\u2022 Infected breeding stock passing it to chicks through eggs\n"
                        + "\u2022 Rodents, wild birds and insects\n"
                        + "\u2022 Dirty crates, boots and hands",
                "\u2022 Separate visibly affected birds where practical\n"
                        + "\u2022 Clean and disinfect drinkers and feeders, and supply fresh water\n"
                        + "\u2022 Remove contaminated litter and improve ventilation\n"
                        + "\u2022 Wash hands and change footwear after handling birds\n"
                        + "\u2022 Contact a qualified poultry veterinarian for diagnosis and treatment",
                "\u2022 Source chicks from Salmonella-monitored hatcheries\n"
                        + "\u2022 Store feed dry and protect it from rodents and wild birds\n"
                        + "\u2022 Control rodents and insects around the house\n"
                        + "\u2022 Clean and disinfect housing thoroughly between flocks\n"
                        + "\u2022 Handle and store eggs hygienically",
                "\u2022 Keep a strict cleaning routine for drinkers and feeders\n"
                        + "\u2022 Keep young and older birds apart\n"
                        + "\u2022 Collect eggs often and keep them clean and cool\n"
                        + "\u2022 Wash hands before and after working with the flock",
                "\u2022 Chick deaths rise in the first weeks\n"
                        + "\u2022 Several birds show pasted vents or severe weakness\n"
                        + "\u2022 Anyone handling the birds becomes unwell\n"
                        + "\u2022 The problem returns after cleaning",
                false));

        map.put("healthy", new DiseaseInfo(
                "healthy",
                "Healthy",
                "Healthy poultry condition",
                "No disease pattern was confidently detected in this image. This is a screening "
                        + "result for the submitted photo only and does not certify the health of "
                        + "the whole flock.",
                "\u2022 Alert, active birds\n"
                        + "\u2022 Normal firm droppings\n"
                        + "\u2022 Steady feed and water intake\n"
                        + "\u2022 Clean vent area and smooth feathers",
                "\u2022 Not applicable - this is not a disease class.\n"
                        + "\u2022 A healthy result only describes this one image, not the flock.",
                "\u2022 Continue normal poultry health monitoring\n"
                        + "\u2022 Keep watching droppings, appetite and behaviour daily\n"
                        + "\u2022 Re-scan if anything changes\n"
                        + "\u2022 Contact a veterinarian if birds look unwell despite this result",
                "\u2022 Keep litter dry and housing well ventilated\n"
                        + "\u2022 Provide clean drinking water and store feed properly\n"
                        + "\u2022 Avoid overcrowding\n"
                        + "\u2022 Maintain routine cleaning and biosecurity",
                "\u2022 Check feed and water lines every day\n"
                        + "\u2022 Weigh or observe growth regularly\n"
                        + "\u2022 Keep records of anything unusual\n"
                        + "\u2022 Keep the vaccination programme up to date",
                "\u2022 Birds look or behave unwell even though this scan said healthy\n"
                        + "\u2022 Feed or water intake drops\n"
                        + "\u2022 Egg production falls\n"
                        + "\u2022 Any unexplained death occurs",
                true));

        BY_LABEL = Collections.unmodifiableMap(map);
    }

    /** Returns null when the key is not one of the four known model classes. */
    public static DiseaseInfo forLabel(String rawLabel) {
        if (rawLabel == null) {
            return null;
        }
        return BY_LABEL.get(rawLabel.trim().toLowerCase(Locale.US));
    }

    /** Display name only, falling back to the raw label. */
    public static String displayNameFor(String rawLabel) {
        DiseaseInfo info = forLabel(rawLabel);
        return info != null ? info.displayName : String.valueOf(rawLabel);
    }
}
