package app.gaborbiro.sparescrape.data.model

class Message(
    val senderName: String,
    val messageLink: String,
    val propertyLinks: List<String>
) {
    override fun toString(): String {
        return "Message(senderName='$senderName', propertyLinks=${propertyLinks.size}, messageLink='$messageLink')"
    }
}

