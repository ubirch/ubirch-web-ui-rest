package com.ubirch.webui.core.operations

import java.util

import com.typesafe.scalalogging.LazyLogging
import com.ubirch.webui.core.ApiUtil
import com.ubirch.webui.core.operations.Devices.bulkCreateDevice
import com.ubirch.webui.core.operations.Groups.addSingleUserToGroup
import com.ubirch.webui.core.operations.Utils.getRealm
import com.ubirch.webui.core.structure.{ AddDevice, User, Elements }
import org.keycloak.admin.client.resource.RealmResource
import org.keycloak.representations.idm.GroupRepresentation

import scala.collection.JavaConverters._

/*
Populate the "test-realm" keycloak realm with 8 users owning between 1 and 10 devices each
A device can have a type defined in listTypes
The attributes of the devices are also listed in listTypes
Changing the api config group attributes can be done by modifying DEFAULT_ATTRIBUTE_API_CONF
changing the max number of devices per user => change value of numberDevicesMaxPerUser
Description of devices are taken randomly from listDescriptions
 */
object BeateLaunchThatIfYouWantANiceWorkspace extends LazyLogging {

  val ds = new DevicesSpec

  implicit val realmName: String = "test-realm"
  implicit val realm: RealmResource = getRealm


  val DEFAULT_ATTRIBUTE_API_CONF = "{\"password\":\"password\"}"
  val DEFAULT_MAP_ATTRIBUTE_API_CONF: util.Map[String, util.List[String]] = Map("attributesApiGroup" -> List(DEFAULT_ATTRIBUTE_API_CONF).asJava).asJava

  val numberDevicesMaxPerUser = 10

  def main(args: Array[String]): Unit = {
    // clear db
    TestUtils.clearKCRealm
    // users representations
    val users: List[User] = List(
      User("", "derMicha", "Merz", "Michael"),
      User("", "leo", "Jugel", "Matthias"),
      User("", "ChrisX", "Elsner", "Christian"),
      User("", "elCarlos", "Sanchez", "Carlos"),
      User("", "dieBeate", "Fiss", "Beate"),
      User("", "leBen", "George", "Benoit"),
      User("", "Waldi", "Grünwald", "Waldermar"),
      User("", "dieLotta", "Rüger", "Lotta")
    )
    // create device groups
    val devicesConfigRepresentation: List[(String, util.Map[String, util.List[String]])] = (for (i <- listTypes.indices) yield (listTypes(i)._1, generateDeviceAttributes(listTypes(i)._2))).toList
    devicesConfigRepresentation.foreach(d => TestUtils.createGroupWithConf(d._2,Elements.PREFIX_DEVICE_TYPE + d._1))

    // create apiConfigGroup
    val apiConfigGroup = TestUtils.createGroupWithConf(DEFAULT_MAP_ATTRIBUTE_API_CONF, realmName + Elements.PREFIX_API + "default")

    // create roles
    TestUtils.createAndGetSimpleRole(Elements.DEVICE)
    TestUtils.createAndGetSimpleRole(Elements.USER)

    // create users
    users foreach { user =>
      createOneUserAndItsDevices(scala.util.Random.nextInt(numberDevicesMaxPerUser) + 1, user, apiConfigGroup.toRepresentation)
    }

  }

  def createOneUserAndItsDevices(numberDevices: Int, userStruct: User, apiConfigGroup: GroupRepresentation): List[String] = {
    val devicesAttributes: List[(String, String, String)] = createDevicesAttributes(numberDevices) // (hwDeviceId, dType, description)
    val userGroupName = ds.createGroupsName(userStruct.username, realmName, "cc")._1
    val userGroup = TestUtils.createSimpleGroup(userGroupName)
    val user = TestUtils.addUserToKC(userStruct)
    ApiUtil.resetUserPassword(user, "password", temporary = false)
    // make user join groups
    addSingleUserToGroup(userGroup.toRepresentation.getId, user.toRepresentation.getId)
    addSingleUserToGroup(apiConfigGroup.getId, user.toRepresentation.getId)

    val ownerId = user.toRepresentation.getId
    // get role
    val userRole = realm.roles().get(Elements.USER)
    TestUtils.addRoleToUser(user, userRole.toRepresentation)
    val listDevices = devicesAttributes map { d =>
      AddDevice(
        hwDeviceId = d._1,
        deviceType = d._2,
        description = d._3
      )
    }
    bulkCreateDevice(ownerId, listDevices)
  }

  def createDevicesAttributes(numberDevices: Int): List[(String, String, String)] = {
    (for (_ <- 1 to numberDevices) yield {
      TestUtils.generateDeviceAttributes(
        description = listDescriptions(scala.util.Random.nextInt(listDescriptions.length)),
        dType = listTypes(scala.util.Random.nextInt(listTypes.length))._1
      )
    }).toList
  }

  def generateDeviceAttributes(str: String): util.Map[String, util.List[String]] = Map("attributesDeviceGroup" -> List(str).asJava).asJava

  val listTypes: List[(String, String)] = List(
    ("default_type", "{\"type\": \"default\"}"),
    ("thermal_sensor", "{\"type\": \"thermal_sensor\"}"),
    ("light_sensor", "{\"type\": \"light_sensor\"}"),
    ("elevator_fail_detection", "{\"type\": \"light_sensor\"}")
  )

  val listDescriptions: List[String] = List(
    "If Purple People Eaters are real… where do they find purple people to eat?",
    "She only paints with bold colors; she does not like pastels.",
    "If the Easter Bunny and the Tooth Fairy had babies would they take your teeth and leave chocolate for you?",
    "She works two jobs to make ends meet; at least, that was her reason for not having time to join us.",
    "If I don’t like something, I’ll stay away from it.",
    "He turned in the research paper on Friday; otherwise, he would have not passed the class.",
    "When I was little I had a car door slammed shut on my hand. I still remember it quite vividly.",
    "I'd rather be a bird than a fish.",
    "I am counting my calories, yet I really want dessert.",
    "She advised him to come back at once.",
    "He said he was not there yesterday; however, many people saw him there.",
    "I currently have 4 windows open up… and I don’t know why.",
    "The river stole the gods.",
    "Joe made the sugar cookies; Susan decorated them.",
    "We have never been to Asia, nor have we visited Africa.",
    "We need to rent a room for our party.",
    "A song can make or ruin a person’s day if they let it get to them.",
    "She was too short to see over the fence.",
    "I will never be this young again. Ever. Oh damn… I just got older.",
    "Writing a list of random sentences is harder than I initially thought it would be.",
    "Mary plays the piano.",
    "I would have gotten the promotion, but my attendance wasn’t good enough.",
    "We have a lot of rain in June.",
    "She folded her handkerchief neatly.",
    "Is it free?",
    "Rock music approaches at high velocity.",
    "Wow, does that work?",
    "I was very proud of my nickname throughout high school but today- I couldn’t be any different to what my nickname was.",
    "Please wait outside of the house.",
    "Should we start class now, or should we wait for everyone to get here?",
    "Wednesday is hump day, but has anyone asked the camel if he’s happy about it?",
    "There were white out conditions in the town; subsequently, the roads were impassable.",
    "Let me help you with your baggage.",
    "She did not cheat on the test, for it was not the right thing to do.",
    "The old apple revels in its authority.",
    "I love eating toasted cheese and tuna sandwiches.",
    "It was getting dark, and we weren’t there yet.",
    "Lets all be unique together until we realise we are all the same.",
    "A purple pig and a green donkey flew a kite in the middle of the night and ended up sunburnt.",
    "The book is in front of the table.",
    "The clock within this blog and the clock on my laptop are 1 hour different from each other.",
    "Check back tomorrow; I will see if the book has arrived.",
    "I want more detailed information.",
    "Two seats were vacant.",
    "Italy is my favorite country; in fact, I plan to spend two weeks there next year.",
    "I often see the time 11:11 or 12:34 on clocks.",
    "The sky is clear; the stars are twinkling.",
    "There was no ice cream in the freezer, nor did they have money to go to the store.",
    "He ran out of money, so he had to stop playing poker.",
    "He didn’t want to go to the dentist, yet he went anyway.",
    "Where do random thoughts come from?",
    "Cats are good pets, for they are clean and are not noisy.",
    "Tom got a small piece of pie.",
    "Hurry!",
    "The waves were crashing on the shore; it was a lovely sight.",
    "Don't step on the broken glass.",
    "She wrote him a long letter, but he didn't read it.",
    "This is the last random sentence I will be writing and I am going to stop mid-sent",
    "The shooter says goodbye to his love.",
    "He told us a very exciting adventure story.",
    "Yeah, I think it's a good environment for learning English.",
    "Abstraction is often one floor above you.",
    "If you like tuna and tomato sauce- try combining the two. It’s really not as bad as it sounds.",
    "A glittering gem is not enough.",
    "I am happy to take your donation; any amount will be greatly appreciated.",
    "The memory we used to share is no longer coherent.",
    "Sometimes, all you need to do is completely make an ass of yourself and laugh it off to realise that life isn’t so bad after all.",
    "Someone I know recently combined Maple Syrup & buttered Popcorn thinking it would taste like caramel popcorn. It didn’t and they don’t recommend anyone else do it either.",
    "The body may perhaps compensates for the loss of a true metaphysics.",
    "My Mum tries to be cool by saying that she likes all the same things that I do.",
    "She always speaks to him in a loud voice.",
    "I checked to make sure that he was still alive.",
    "This is a Japanese doll.",
    "They got there early, and they got really good seats.",
    "Sometimes it is better to just walk away from things and go back to them later when you’re in a better frame of mind.",
    "Last Friday in three week’s time I saw a spotted striped blue worm shake hands with a legless lizard.",
    "I am never at home on Sundays.",
    "I think I will buy the red car, or I will lease the blue one.",
    "Malls are great places to shop; I can find everything I need under one roof.",
    "Christmas is coming.",
    "How was the math test?",
    "She did her best to help him.",
    "The mysterious diary records the voice.",
    "The quick brown fox jumps over the lazy dog.",
    "What was the person thinking when they discovered cow’s milk was fine for human consumption… and why did they do it in the first place!?",
    "I really want to go to work, but I am too sick to drive.",
    "The stranger officiates the meal.",
    "I hear that Nancy is very pretty.",
    "Everyone was busy, so I went to the movie alone.",
    "I want to buy a onesie… but know it won’t suit me.",
    "She borrowed the book from him many years ago and hasn't yet returned it.",
    "The lake is a long way from here.",
    "Sixty-Four comes asking for bread.",
    "I currently have 4 windows open up… and I don’t know why.",
    "She folded her handkerchief neatly.",
    "The river stole the gods.",
    "He said he was not there yesterday; however, many people saw him there.",
    "Don't step on the broken glass.",
    "A glittering gem is not enough.",
    "Where do random thoughts come from?",
    "Mary plays the piano.",
    "Lets all be unique together until we realise we are all the same.",
    "Tom got a small piece of pie.",
    "I think I will buy the red car, or I will lease the blue one.",
    "He turned in the research paper on Friday; otherwise, he would have not passed the class.",
    "How was the math test?",
    "Someone I know recently combined Maple Syrup & buttered Popcorn thinking it would taste like caramel popcorn. It didn’t and they don’t recommend anyone else do it either.",
    "The memory we used to share is no longer coherent.",
    "She always speaks to him in a loud voice.",
    "She only paints with bold colors; she does not like pastels.",
    "If Purple People Eaters are real… where do they find purple people to eat?",
    "He didn’t want to go to the dentist, yet he went anyway.",
    "A purple pig and a green donkey flew a kite in the middle of the night and ended up sunburnt.",
    "She did her best to help him.",
    "I was very proud of my nickname throughout high school but today- I couldn’t be any different to what my nickname was.",
    "She works two jobs to make ends meet; at least, that was her reason for not having time to join us.",
    "The mysterious diary records the voice.",
    "Should we start class now, or should we wait for everyone to get here?",
    "It was getting dark, and we weren’t there yet.",
    "There were white out conditions in the town; subsequently, the roads were impassable.",
    "Writing a list of random sentences is harder than I initially thought it would be.",
    "There was no ice cream in the freezer, nor did they have money to go to the store.",
    "Check back tomorrow; I will see if the book has arrived.",
    "A song can make or ruin a person’s day if they let it get to them.",
    "Christmas is coming.",
    "She did not cheat on the test, for it was not the right thing to do.",
    "Joe made the sugar cookies; Susan decorated them.",
    "Cats are good pets, for they are clean and are not noisy.",
    "Abstraction is often one floor above you.",
    "If you like tuna and tomato sauce- try combining the two. It’s really not as bad as it sounds.",
    "I want more detailed information.",
    "Please wait outside of the house.",
    "I checked to make sure that he was still alive.",
    "Hurry!",
    "Wednesday is hump day, but has anyone asked the camel if he’s happy about it?",
    "Yeah, I think it's a good environment for learning English.",
    "Last Friday in three week’s time I saw a spotted striped blue worm shake hands with a legless lizard.",
    "The quick brown fox jumps over the lazy dog.",
    "If the Easter Bunny and the Tooth Fairy had babies would they take your teeth and leave chocolate for you?",
    "I hear that Nancy is very pretty.",
    "When I was little I had a car door slammed shut on my hand. I still remember it quite vividly.",
    "The waves were crashing on the shore; it was a lovely sight.",
    "He told us a very exciting adventure story.",
    "What was the person thinking when they discovered cow’s milk was fine for human consumption… and why did they do it in the first place!?",
    "We have a lot of rain in June.",
    "The clock within this blog and the clock on my laptop are 1 hour different from each other.",
    "We need to rent a room for our party.",
    "Italy is my favorite country; in fact, I plan to spend two weeks there next year.",
    "I really want to go to work, but I am too sick to drive.",
    "They got there early, and they got really good seats.",
    "Two seats were vacant.",
    "I am happy to take your donation; any amount will be greatly appreciated.",
    "Sometimes, all you need to do is completely make an ass of yourself and laugh it off to realise that life isn’t so bad after all.",
    "The shooter says goodbye to his love.",
    "I am never at home on Sundays.",
    "Rock music approaches at high velocity.",
    "Everyone was busy, so I went to the movie alone.",
    "The lake is a long way from here.",
    "The sky is clear; the stars are twinkling.",
    "I want to buy a onesie… but know it won’t suit me.",
    "The old apple revels in its authority.",
    "She wrote him a long letter, but he didn't read it.",
    "Wow, does that work?",
    "We have never been to Asia, nor have we visited Africa.",
    "I would have gotten the promotion, but my attendance wasn’t good enough.",
    "The body may perhaps compensates for the loss of a true metaphysics.",
    "I will never be this young again. Ever. Oh damn… I just got older.",
    "I'd rather be a bird than a fish.",
    "She borrowed the book from him many years ago and hasn't yet returned it.",
    "She was too short to see over the fence.",
    "I often see the time 11:11 or 12:34 on clocks.",
    "Sometimes it is better to just walk away from things and go back to them later when you’re in a better frame of mind.",
    "Sixty-Four comes asking for bread.",
    "I am counting my calories, yet I really want dessert.",
    "This is a Japanese doll.",
    "The stranger officiates the meal.",
    "Malls are great places to shop; I can find everything I need under one roof.",
    "This is the last random sentence I will be writing and I am going to stop mid-sent",
    "My Mum tries to be cool by saying that she likes all the same things that I do.",
    "I love eating toasted cheese and tuna sandwiches.",
    "Is it free?",
    "The book is in front of the table.",
    "He ran out of money, so he had to stop playing poker.",
    "Let me help you with your baggage.",
    "She advised him to come back at once.",
    "If I don’t like something, I’ll stay away from it."
  )

}
