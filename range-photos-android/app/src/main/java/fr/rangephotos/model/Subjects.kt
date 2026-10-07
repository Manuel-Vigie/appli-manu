package fr.rangephotos.model

/** Un sujet que l'appli sait reconnaître, avec les noms que lui donne le modèle d'images (en anglais, en minuscules). */
data class Subject(val name: String, val labels: Set<String>)

object Subjects {
    val ALL: List<Subject> = listOf(
        Subject("Voitures", setOf("car", "motor vehicle", "land vehicle", "automotive design", "vehicle", "sedan", "suv", "sports car", "wheel", "tire")),
        Subject("Motos", setOf("motorcycle", "motor scooter", "moped")),
        Subject("Vélos", setOf("bicycle", "bicycle wheel", "mountain bike")),
        Subject("Camions et bus", setOf("truck", "bus", "van", "commercial vehicle", "tractor")),
        Subject("Bateaux", setOf("boat", "ship", "watercraft", "sailboat", "yacht")),
        Subject("Avions", setOf("airplane", "aircraft", "aviation", "helicopter")),
        Subject("Trains", setOf("train", "locomotive", "railway", "tram")),
        Subject("Chiens", setOf("dog", "puppy")),
        Subject("Chats", setOf("cat", "kitten")),
        Subject("Oiseaux", setOf("bird", "duck", "eagle", "parrot")),
        Subject("Chevaux et vaches", setOf("horse", "cattle", "cow", "sheep", "livestock")),
        Subject("Poissons et mer", setOf("fish", "marine biology", "underwater")),
        Subject("Insectes", setOf("insect", "butterfly", "bee", "spider")),
        Subject("Fleurs", setOf("flower", "petal", "rose")),
        Subject("Arbres et forêts", setOf("tree", "forest", "woody plant", "jungle")),
        Subject("Plantes", setOf("plant", "leaf", "grass", "garden")),
        Subject("Montagnes", setOf("mountain", "mountainous landforms", "hill", "cliff", "valley")),
        Subject("Mer et plage", setOf("sea", "beach", "coast", "ocean", "shore", "sand")),
        Subject("Lacs et rivières", setOf("lake", "river", "waterfall", "water", "pond")),
        Subject("Ciel et nuages", setOf("sky", "cloud", "sunset", "sunrise", "horizon")),
        Subject("Neige", setOf("snow", "winter", "ice", "glacier")),
        Subject("Bâtiments", setOf("building", "house", "architecture", "church", "castle", "tower", "bridge", "city", "skyscraper")),
        Subject("Nourriture", setOf("food", "dish", "meal", "cuisine", "pizza", "bread", "meat", "cake")),
        Subject("Fruits et légumes", setOf("fruit", "vegetable", "produce", "natural foods")),
        Subject("Boissons", setOf("drink", "beer", "wine", "coffee", "cocktail", "bottle")),
        Subject("Affiches et textes", setOf("poster", "text", "font", "paper", "document", "signage", "sign")),
        Subject("Meubles", setOf("furniture", "table", "chair", "couch", "bed", "room")),
        Subject("Vêtements", setOf("clothing", "shoe", "footwear", "jacket", "hat")),
        Subject("Sport", setOf("sports", "ball", "skiing", "football", "running", "swimming")),
        Subject("Musique", setOf("musical instrument", "guitar", "piano", "concert")),
        Subject("Écrans et appareils", setOf("computer", "television", "electronics", "gadget", "mobile phone", "screen")),
    )

    /** Noms français des sujets couverts par [labels]. */
    fun namesFor(labels: Set<String>): List<String> =
        ALL.filter { subject -> subject.labels.any { it in labels } }.map { it.name }.ifEmpty { listOf("sujets choisis") }

    fun labelsOf(names: Collection<String>): Set<String> =
        ALL.filter { it.name in names }.flatMap { it.labels }.toSet()

    private fun labelsOfNames(vararg names: String): Set<String> = labelsOf(names.toList())

    /** Dossiers proposés d'office (désactivés tant que l'utilisateur ne les allume pas). */
    fun presets(): List<Category> = listOf(
        Category("barcode", "Codes-barres", CategoryKind.BARCODE, builtIn = true),
        Category(
            "vehicles", "Véhicules", CategoryKind.SUBJECTS,
            labelsOfNames("Voitures", "Motos", "Vélos", "Camions et bus", "Bateaux", "Avions", "Trains"), builtIn = true,
        ),
        Category("places", "Lieux", CategoryKind.PLACE, builtIn = true),
        Category("animals", "Animaux", CategoryKind.SUBJECTS, labelsOfNames("Chiens", "Chats", "Oiseaux", "Chevaux et vaches", "Poissons et mer", "Insectes"), builtIn = true),
        Category("food", "Nourriture", CategoryKind.SUBJECTS, labelsOfNames("Nourriture", "Fruits et légumes", "Boissons"), builtIn = true),
        Category("plants", "Fleurs et plantes", CategoryKind.SUBJECTS, labelsOfNames("Fleurs", "Plantes", "Arbres et forêts"), builtIn = true),
        Category("landscapes", "Paysages", CategoryKind.SUBJECTS, labelsOfNames("Montagnes", "Mer et plage", "Lacs et rivières", "Ciel et nuages", "Neige"), builtIn = true),
    )
}
