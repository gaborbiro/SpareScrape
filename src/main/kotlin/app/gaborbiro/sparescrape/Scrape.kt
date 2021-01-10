package app.gaborbiro.sparescrape

import app.gaborbiro.sparescrape.data.model.LonLat
import app.gaborbiro.sparescrape.data.model.MessageLinkSet
import app.gaborbiro.sparescrape.data.model.Property
import org.openqa.selenium.By
import org.openqa.selenium.WebDriver
import org.openqa.selenium.WebElement

object Scrape {

    fun scrapeLinksFromMessages(driver: WebDriver): List<MessageLinkSet> {
        driver["${ROOT_URL}/flatshare/mythreads.pl"]
        val messages = mutableListOf<MessageLinkSet>()

        runCatching {
            driver.findElement(By.linkText("Oldest first")).click()
            val rawMessages = driver.findElements(By.className("msg_row"))

            if (rawMessages.isEmpty()) {
                println("no messages found")
            } else {
                rawMessages[0].click()
                do {
                    val senderName = driver.findElement(By.className("message_in__name")).text
                    println(senderName)
                    val messageBody: WebElement = driver.findElement(By.xpath("//dd[@class='message_body']"))
                    val links = mutableListOf<String>().apply {
                        getLinks(driver, messageBody, this)
                    }
                    messages.add(MessageLinkSet(senderName, links))
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

    private fun getLinks(driver: WebDriver, element: WebElement, output: MutableList<String>) {
        element.findElements(By.xpath("*")).forEach {
            var href = it.getAttribute("href")
            if (!href.isNullOrBlank()) {
                href = if (href.startsWith(ROOT_URL_MOBILE)) {
                    href.replace(ROOT_URL_MOBILE, ROOT_URL)
                } else href
                output.add(href)
            }
            getLinks(driver, it, output)
        }
    }

    fun scrapeProperty(driver: WebDriver, propertyUrl: String, senderName: String? = null): Property? {
        return if (propertyUrl.startsWith(ROOT_URL)) {
            driver[propertyUrl]
            with(driver) {
                val price = findSimpleText(
                    "//div[@class=\"property-details\"]/section[@class=\"feature feature--price_room_only\"]/ul/li[1]/strong"
                )
                Property(
                    url = propertyUrl,
                    senderName = senderName ?: "",
                    price = price,
                    pricePerMonth = perWeekToPerMonth(price),
                    location = findRegex("latitude: \"(.*?)\",longitude: \"(.*?)\"").let { LonLat(it[0], it[1]) },
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
        } else {
            println("Not a spareroom link: $propertyUrl ($senderName)")
            null
        }
    }

    private fun WebDriver.findRegex(regex: String): Array<String> {
        val matcher = pageSource.matcher(regex)
        return if (matcher.find()) {
            (1..matcher.groupCount()).map { matcher.group(it) }.toTypedArray()
        } else {
            null
        }!!
    }

    private fun WebDriver.findSimpleText(xpath: String): String {
        return kotlin.runCatching { findElement(By.xpath(xpath)).text }.getOrNull() ?: run {
            println("Could not find element: $xpath")
            MISSING_VALUE
        }
    }
}