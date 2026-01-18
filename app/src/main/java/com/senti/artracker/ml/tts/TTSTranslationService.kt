package com.senti.artracker.ml.tts

object TTSTranslationService {

    private val signs = mapOf(

        "Speed Limit 20 km/h" to SignInfo(
            "Speed Limit Sign",
            "Speed Limit 20 km/h",
            "Maximum allowed speed is 20 kilometers per hour."
        ),

        "Speed Limit 30 km/h" to SignInfo(
            "Speed Limit Sign",
            "Speed Limit 30 km/h",
            "Maximum allowed speed is 30 kilometers per hour."
        ),

        "Speed Limit 50 km/h" to SignInfo(
            "Speed Limit Sign",
            "Speed Limit 50 km/h",
            "Maximum allowed speed is 50 kilometers per hour."
        ),

        "Speed Limit 60 km/h" to SignInfo(
            "Speed Limit Sign",
            "Speed Limit 60 km/h",
            "Maximum allowed speed is 60 kilometers per hour."
        ),

        "Speed Limit 70 km/h" to SignInfo(
            "Speed Limit Sign",
            "Speed Limit 70 km/h",
            "Maximum allowed speed is 70 kilometers per hour."
        ),

        "Speed Limit 80 km/h" to SignInfo(
            "Speed Limit Sign",
            "Speed Limit 80 km/h",
            "Maximum allowed speed is 80 kilometers per hour."
        ),

        "End of Speed Limit 80 km/h" to SignInfo(
            "End of Speed Limit",
            "End of Speed Limit 80 km/h",
            "The previously imposed speed limit of 80 kilometers per hour is no longer valid."
        ),

        "Speed Limit 100 km/h" to SignInfo(
            "Speed Limit Sign",
            "Speed Limit 100 km/h",
            "Maximum allowed speed is 100 kilometers per hour."
        ),

        "Speed Limit 120 km/h" to SignInfo(
            "Speed Limit Sign",
            "Speed Limit 120 km/h",
            "Maximum allowed speed is 120 kilometers per hour."
        ),

        "Stop" to SignInfo(
            "Stop Sign",
            "Stop",
            "You must bring the vehicle to a complete stop and give way to all other traffic."
        ),

        "Yield" to SignInfo(
            "Yield Sign",
            "Yield",
            "You must slow down and give priority to vehicles on the intersecting road."
        ),

        "No entry" to SignInfo(
            "No Entry Sign",
            "No entry",
            "Entry for all vehicles is prohibited."
        ),

        "No vehicles" to SignInfo(
            "No Vehicles Sign",
            "No vehicles",
            "All motor vehicles are prohibited from entering this road."
        ),

        "Priority road" to SignInfo(
            "Priority Road Sign",
            "Priority road",
            "You are traveling on a road with priority at intersections."
        ),

        "Right-of-way at the next intersection" to SignInfo(
            "Right-of-Way Sign",
            "Right-of-way at the next intersection",
            "You have priority over vehicles approaching from side roads."
        ),

        "General caution" to SignInfo(
            "Warning Sign",
            "General caution",
            "Pay special attention and be prepared for unexpected road conditions."
        ),

        "Pedestrians" to SignInfo(
            "Pedestrian Crossing Warning",
            "Pedestrians",
            "Watch out for pedestrians crossing the road."
        ),

        "Children crossing" to SignInfo(
            "Children Crossing Warning",
            "Children crossing",
            "Reduce speed and watch for children near the roadway."
        ),

        "Wild animals crossing" to SignInfo(
            "Wild Animals Warning",
            "Wild animals crossing",
            "Be alert for wild animals that may enter the road."
        ),

        "Roundabout mandatory" to SignInfo(
            "Roundabout Sign",
            "Roundabout mandatory",
            "You must follow the roundabout and yield to traffic already inside."
        ),

        "Keep right" to SignInfo(
            "Keep Right Sign",
            "Keep right",
            "You must pass the obstacle on the right-hand side."
        ),

        "Keep left" to SignInfo(
            "Keep Left Sign",
            "Keep left",
            "You must pass the obstacle on the left-hand side."
        ),

        "End of all speed and passing limits" to SignInfo(
            "End of Restrictions Sign",
            "End of all speed and passing limits",
            "All previous speed and passing restrictions are lifted."
        )
    )

    fun translate(label: String): String =
        signs[label]?.toTTSText()
        ?: "Sign detected. Translation not found"
}
