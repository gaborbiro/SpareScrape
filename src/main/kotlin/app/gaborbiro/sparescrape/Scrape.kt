package app.gaborbiro.sparescrape

import app.gaborbiro.sparescrape.data.Preferences
import app.gaborbiro.sparescrape.data.model.Cookies
import app.gaborbiro.sparescrape.data.model.LonLat
import app.gaborbiro.sparescrape.data.model.Message
import app.gaborbiro.sparescrape.data.model.Property
import com.google.gson.Gson
import org.openqa.selenium.By
import org.openqa.selenium.UnexpectedAlertBehaviour
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement
import org.openqa.selenium.chrome.ChromeDriver
import org.openqa.selenium.chrome.ChromeOptions
import org.openqa.selenium.remote.CapabilityType
import java.nio.file.Paths

object Scrape {

    private val gson = Gson()
    private lateinit var driver: WebDriver

    /**
     * Requires same webpage to stay open
     */
    fun scrapeMessages(): List<Message> {
        ensureBrowser()
        driver["${ROOT_URL}/flatshare/mythreads.pl"] // open inbox
        val messages = mutableListOf<Message>()

        runCatching {
            driver.findElement(By.linkText("Oldest first")).click()
            val rawMessages = driver.findElements(By.className("msg_row"))

            if (rawMessages.isEmpty()) {
                println("no messages found")
            } else {
                rawMessages[0].click()
                do {
                    if (getMessageTags().isEmpty()) { // ignoring tagged messages
                        val message = scrapeMessage()
                        if (message.propertyLinks.isEmpty()) {
                            tagMessage(driver.currentUrl, openUrl = false, Tag.NO_LINKS)
                        } else {
                            messages.add(message)
                        }
                    }
                } while (runCatching { driver.findElement(By.linkText("Next thread»")) }.let {
                        it.getOrNull()?.click()
                        it.isSuccess
                    })
            }
        }.apply {
            if (isFailure) {
                this.exceptionOrNull()?.printStackTrace()
            }
        }
        return messages
    }

    /**
     * Assumes message is open
     */
    private fun getMessageTags(): List<String> {
        return kotlin.runCatching { driver.findElements(By.className("add-label__attached-label")) }
            .getOrNull()?.map { it.text } ?: emptyList()
    }

    /**
     * Assumes message is open
     */
    private fun scrapeMessage(): Message {
        val senderName = driver.findElement(By.className("message_in__name")).text
        val messageBody = driver.findElement(By.xpath("//dd[@class='message_body']"))
        val links = mutableListOf<String>().apply {
            getLinksFromMessage(messageBody, output = this)
        }
        if (links.isEmpty()) { // try the top "view their ad" link
            getCurrentAdLink()?.let { links.add(it) }
        }
        println(senderName + " (${links.size} links)")
        return Message(senderName, driver.currentUrl, links)
    }

    /**
     * Assumes message is open
     */
    private fun getCurrentAdLink(): String? {
        return kotlin.runCatching { driver.findElement(By.linkText("view their ad")) }.getOrNull()?.getAttribute("href")
    }

    private fun getLinksFromMessage(element: WebElement, output: MutableList<String>) {
        element.findElements(By.xpath("*")).forEach {
            val href = it.getAttribute("href")
            if (!href.isNullOrBlank()) {
                output.add(cleanUrl(href))
            }
            getLinksFromMessage(it, output)
        }
    }

    fun scrapeSearch(searchResultsUrl: String): List<String> {
        ensureBrowser()
        driver[searchResultsUrl]
        return driver.findElements(By.className("listing-result")).mapNotNull {
            if (runCatching { it.findElement(By.linkText("Unsuitable")) }.getOrNull() != null) {
                // Featured properties will appear in the search result even if marked as unsuitable
                null
            } else {
                it.findElement(By.linkText("More info")).getAttribute("href")
            }
        }
    }

    fun scrapeProperty(propertyUrl: String, senderName: String?, messageUrl: String?): Property {
        ensureBrowser()
        driver[propertyUrl]
        with(driver) {
            val pricesXpath =
                "//div[@class=\"property-details\"]/section[@class=\"feature feature--price_room_only\"]/ul/li"
            val prices: Array<String>? = runCatching { findElements(By.xpath(pricesXpath)) }.getOrNull()?.mapNotNull {
                val price = it.findElement(By.xpath("strong")).text
                val comment = it.findElement(By.xpath("small")).text
                if (!comment.contains("NOW LET")) price else null
            }?.toTypedArray()
            return Property(
                url = propertyUrl,
                title = findSimpleText("//h1[1]"),
                unsuitable = linkExists("Marked as Unsuitable"),
                senderName = senderName,
                messageUrl = messageUrl,
                prices = prices?.let { perWeekToPerMonth(prices) } ?: emptyArray(),
                location = findRegex("latitude: \"(.*?)\",longitude: \"(.*?)\"")?.let { LonLat(it[0], it[1]) },
                billIncluded = findSimpleText(
                    "//dt[@class=\"feature-list__key\" and text()=\"Bills included?\"]/following-sibling::dd[1]"
                ),
                deposit = findSimpleText(
                    "//dt[@class=\"feature-list__key\" and text()=\"Deposit\"]/following-sibling::dd[1]"
                ),
                available = findSimpleText(
                    "//dt[@class=\"feature-list__key\" and text()=\"Available\"]/following-sibling::dd[1]"
                ),
                minTerm = findSimpleText(
                    "//dt[@class=\"feature-list__key\" and text()=\"Minimum term\"]/following-sibling::dd[1]"
                ),
                maxTerm = findSimpleText(
                    "//dt[@class=\"feature-list__key\" and text()=\"Maximum term\"]/following-sibling::dd[1]"
                ),
                furnishings = findSimpleText(
                    "//dt[@class=\"feature-list__key\" and text()=\"Furnishings\"]/following-sibling::dd[1]"
                ),
                broadband = findSimpleText(
                    "//dt[@class=\"feature-list__key\" and text()=\"Broadband\"]/following-sibling::dd[1]"
                ),
                livingRoom = findSimpleText(
                    "//dt[@class=\"feature-list__key\" and text()=\"Living room\"]/following-sibling::dd[1]"
                ),
                flatmates = findSimpleText(
                    "//section[@class=\"feature feature--current-household\"]//dt[@class=\"feature-list__key\" and (contains(text(),\"flatmates\") or contains(text(),\"housemates\"))]/following-sibling::dd[1]"
                ),
                totalRooms = findSimpleText(
                    "//section[@class=\"feature feature--current-household\"]//dt[@class=\"feature-list__key\" and contains(text(),\"Total # rooms\")]/following-sibling::dd[1]"
                ),
                householdGender = findSimpleText(
                    "//section[@class=\"feature feature--current-household\"]//dt[@class=\"feature-list__key\" and contains(text(),\"Gender\")]/following-sibling::dd[1]"
                ),
                preferredGender = findSimpleText(
                    "//section[@class=\"feature feature--household-preferences\"]//dt[@class=\"feature-list__key\" and contains(text(),\"Gender\")]/following-sibling::dd[1]"
                ),
                occupation = findSimpleText(
                    "//section[@class=\"feature feature--household-preferences\"]//dt[@class=\"feature-list__key\" and contains(text(),\"Occupation\")]/following-sibling::dd[1]"
                )
            )
        }
    }

    /**
     * Assumes property is open
     */
    fun checkForBuddyUp(): Boolean {
        return driver.findElements(By.className("key-features__feature")).any {
            it.text.contains("wanted")
        }
    }

    fun markAsUnsuitable(propertyUrl: String, openUrl: Boolean) {
        ensureBrowser()
        if (openUrl) driver[propertyUrl]
        runCatching { driver.findElement(By.linkText("Mark as unsuitable")) }.getOrNull()
            ?.let {
                it.click()
                println("Marked as unsuitable")
                return
            }
        runCatching { driver.findElement(By.linkText("Saved - remove ad")) }.getOrNull()?.let {
            it.click()
            driver[propertyUrl]
            runCatching { driver.findElement(By.linkText("Mark as unsuitable")) }.getOrNull()
                ?.let {
                    it.click()
                    println("Marked as unsuitable")
                    return
                }
        }
        runCatching { driver.findElement(By.linkText("Contacted")) }.getOrNull()?.let {
            it.click()
            runCatching { driver.findElement(By.xpath("//input[@value='unsuitable']")) }.getOrNull()?.click()
            runCatching { driver.findElement(By.className("submit")) }.getOrNull()?.click()
            println("Marked as unsuitable")
            return
        }
        println("Failed to mark as unsuitable")
    }

    fun tagMessage(messageUrl: String, openUrl: Boolean = false, vararg tags: Tag) {
        ensureBrowser()
        if (openUrl) driver[messageUrl]
        tags.forEach { tag ->
            runCatching { driver.findElement(By.className("add-label__link")) }.getOrNull()?.let {
                it.click() // open tag menu
                runCatching { driver.findElement(By.id(tag.id)) }.getOrNull()
                    ?.click()
                    ?: run { driver.findElement(By.className("add-label__close")).click() }
            }
        }
    }

    private fun WebDriver.findRegex(regex: String): Array<String>? {
        val matcher = pageSource.matcher(regex)
        return if (matcher.find()) {
            (1..matcher.groupCount()).map { matcher.group(it) }.toTypedArray()
        } else {
            null
        }
    }

    private fun WebDriver.findSimpleText(xpath: String): String {
        return runCatching { findElement(By.xpath(xpath)).text }.getOrNull() ?: run {
            MISSING_VALUE
        }
    }

    private fun WebDriver.linkExists(linkText: String): Boolean {
        return runCatching { findElement(By.linkText(linkText)) }.getOrNull() != null
    }

    private fun ensureBrowser() {
        if (!::driver.isInitialized) {
            System.setProperty(
                "webdriver.chrome.driver",
                Paths.get("src/main/resources/chromedriver_win32/chromedriver.exe").toString()
            )
            driver = ChromeDriver(
                ChromeOptions().apply {
                    addArguments("start-maximized")
                    setCapability(CapabilityType.UNEXPECTED_ALERT_BEHAVIOUR, UnexpectedAlertBehaviour.DISMISS)
                }
            )

            driver[ROOT_URL]
            Preferences.get(PREF_COOKIES, null)?.let {
                runCatching {
                    val cookies: Cookies = gson.fromJson(it, Cookies::class.java)
                    cookies.cookies.forEach { driver.manage().addCookie(it) }
                    driver[ROOT_URL]
                }
            }

            if (runCatching { driver.findElement(By.linkText("Log In")) }.isSuccess) {
                login()
            }
        }
    }

    fun cleanup() {
        if (::driver.isInitialized) {
            driver.quit()
        }
    }

    private fun login() {
        driver.findElement(By.id("show-user-auth-popup")).click()
        driver.findElement(By.id("loginemail")).click()
        driver.findElement(By.id("loginemail")).sendKeys("gabor.biro@yahoo.com")
        driver.findElement(By.id("loginpass")).click()
        driver.findElement(By.id("loginpass")).sendKeys("6euBDNW9JUssLwy")
        driver.findElement(By.id("sign-in-button")).click()

        val cookies = Cookies(driver.manage().cookies)
        Preferences.save(PREF_COOKIES, gson.toJson(cookies))
    }

    fun clearCookies() {
        Preferences.clear(PREF_COOKIES)
    }
}

private const val PREF_COOKIES = "cookies"
