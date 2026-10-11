package com.universalrp.cleansweep.ai

/** A configured online mode is not proof a cloud model has answered; greetings make no API call. */
object AssistantModePolicy {
    fun welcome(online: Boolean, ready: Boolean, tamil: Boolean): String = when {
        tamil && online && ready -> "வணக்கம்! இது லைவ் கார்டு உதவியாளர். ஆன்லைன் AI முறை தேர்ந்தெடுக்கப்பட்டுள்ளது; உங்கள் கேள்வி தேர்ந்தெடுத்த சேவைக்கு அனுப்பப்படலாம். இந்த வரவேற்பு உள்ளூரில் உருவாக்கப்பட்டது; மாடல் இயங்கியது என்பதற்குச் சான்றல்ல."
        tamil && online -> "வணக்கம்! இது லைவ் கார்டு உதவியாளர். ஆன்லைன் முறை தேர்ந்தெடுக்கப்பட்டுள்ளது; ஆனால் சேவையை இன்னும் அமைக்க வேண்டும். அதுவரை உள்ளூர் உதவி கிடைக்கும்."
        tamil -> "வணக்கம்! இது லைவ் கார்டு உதவியாளர். இப்போது உள்ளூர்/ஆஃப்லைன் முறை; பதில்கள் இந்தப் போனில் கிடைக்கும் தகவல்களையும் உள்ளமைந்த விதிகளையும் பயன்படுத்தும். பொது கேள்விகளுக்கு விருப்பப்பட்டால் ஆன்லைன் AI முறையை இயக்கலாம்."
        online && ready -> "Hello! I'm the Live Guard assistant. Online AI mode is selected; your question may be sent to the selected provider. This welcome is generated locally, not proof that the configured model has answered."
        online -> "Hello! I'm the Live Guard assistant. Online mode is selected, but the provider still needs setup. Local help remains available."
        else -> "Hello! I'm the Live Guard assistant. Local/offline mode is selected; replies use this phone's available readings and built-in rules. You can enable online AI for general questions if you choose."
    }
}
