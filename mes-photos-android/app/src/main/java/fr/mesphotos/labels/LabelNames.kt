package fr.mesphotos.labels

/**
 * Les étiquettes de la reconnaissance de Google sont en anglais : on garde les plus utiles et on les met en français.
 * Les étiquettes trop vagues (« Photograph », « Fun »…) ne sont pas dans la liste : elles sont ignorées.
 */
object LabelNames {
    private val FRENCH: Map<String, String> = mapOf(
        "beach" to "Plage", "sea" to "Mer", "ocean" to "Mer", "coast" to "Mer", "shore" to "Plage", "sand" to "Plage",
        "lake" to "Lac", "river" to "Rivière", "waterfall" to "Cascade", "pond" to "Étang", "swimming pool" to "Piscine", "pool" to "Piscine",
        "mountain" to "Montagne", "hill" to "Colline", "snow" to "Neige", "skiing" to "Ski", "ski" to "Ski", "glacier" to "Neige", "cave" to "Grotte",
        "forest" to "Forêt", "tree" to "Arbre", "flower" to "Fleur", "plant" to "Plante", "garden" to "Jardin", "grass" to "Prairie",
        "field" to "Champ", "desert" to "Désert", "sunset" to "Coucher de soleil", "sunrise" to "Lever de soleil", "sky" to "Ciel", "cloud" to "Ciel", "night" to "Nuit",
        "dog" to "Chien", "cat" to "Chat", "bird" to "Oiseau", "horse" to "Cheval", "fish" to "Poisson", "cow" to "Vache", "insect" to "Insecte", "butterfly" to "Papillon",
        "car" to "Voiture", "vehicle" to "Véhicule", "truck" to "Camion", "motorcycle" to "Moto", "bicycle" to "Vélo", "bus" to "Bus", "boat" to "Bateau", "ship" to "Bateau",
        "airplane" to "Avion", "aircraft" to "Avion", "train" to "Train", "license plate" to "Plaque d'immatriculation", "motor vehicle" to "Véhicule", "wheel" to "Véhicule",
        "food" to "Repas", "meal" to "Repas", "dish" to "Repas", "cuisine" to "Repas", "pizza" to "Repas", "cake" to "Gâteau", "dessert" to "Dessert", "fruit" to "Fruits",
        "vegetable" to "Légumes", "coffee" to "Café", "wine" to "Vin", "beer" to "Bière", "drink" to "Boisson", "cocktail" to "Boisson", "restaurant" to "Restaurant",
        "building" to "Bâtiment", "house" to "Maison", "tower" to "Tour", "bridge" to "Pont", "church" to "Église", "castle" to "Château", "monument" to "Monument",
        "skyscraper" to "Ville", "city" to "Ville", "street" to "Rue", "road" to "Route", "urban area" to "Ville", "town" to "Ville", "village" to "Village",
        "room" to "Intérieur", "living room" to "Intérieur", "kitchen" to "Cuisine", "bedroom" to "Chambre", "furniture" to "Intérieur", "bathroom" to "Salle de bain",
        "text" to "Document", "paper" to "Document", "document" to "Document", "font" to "Document", "handwriting" to "Document", "screenshot" to "Capture d'écran",
        "selfie" to "Selfie", "smile" to "Portrait", "face" to "Portrait", "crowd" to "Foule", "baby" to "Bébé", "toddler" to "Enfant", "child" to "Enfant",
        "wedding" to "Mariage", "bride" to "Mariage", "party" to "Fête", "event" to "Fête", "concert" to "Concert", "stadium" to "Stade", "sport" to "Sport",
        "football" to "Sport", "tennis" to "Sport", "golf" to "Sport", "swimming" to "Baignade", "camping" to "Camping", "tent" to "Camping", "hiking" to "Randonnée",
        "toy" to "Jouet", "christmas" to "Noël", "christmas tree" to "Noël", "fireworks" to "Feu d'artifice", "pet" to "Animaux", "animal" to "Animaux",
        "computer" to "Informatique", "laptop" to "Informatique", "mobile phone" to "Téléphone", "television" to "Télévision",
        "painting" to "Tableau", "art" to "Art", "museum" to "Musée", "temple" to "Temple", "statue" to "Statue",
    )

    /** Le mot français pour la meilleure étiquette connue (celle dont la confiance est la plus haute), ou null. */
    fun pick(labels: List<Pair<String, Float>>): String? =
        labels.filter { FRENCH.containsKey(it.first.lowercase()) }
            .maxByOrNull { it.second }
            ?.let { FRENCH[it.first.lowercase()] }
}
