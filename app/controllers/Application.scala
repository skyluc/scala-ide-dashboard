package controllers

import play.api._
import play.api.mvc._
import play.api.libs.ws.WS
import scala.concurrent.Future
import model.PullRequest
import model.Project
import play.Play
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Concurrent.Channel
import play.api.libs.iteratee.Iteratee
import views.xml.project

object Application extends Controller {

  private val OAuthToken = Play.application().configuration().getString("dashboard.oauthtoken")

  private val BaseGitHubURL = "https://api.github.com/"
  private val RepoAPI = "repos/"

  private val repo = "scala-ide/scala-ide"

  private val PullRequestCommand = "/pulls?"

  private val AccessTokenParam = s"access_token=$OAuthToken"

  implicit val context = scala.concurrent.ExecutionContext.Implicits.global

  def index =  Action { request =>
    Ok(views.html.index(Project.allProjects))
  }

  def ws = WebSocket.using[String] { request =>

    //Concurernt.broadcast returns (Enumerator, Concurrent.Channel)
    val (out, channel) = Concurrent.broadcast[String]

    //log the message to stdout and send response back to client
    val in = Iteratee.foreach[String] {
      msg =>
        println(msg)
        populateProjects(channel)
    }
    (in, out)
  }

  def populateProjects(channel: Channel[String]) = {
    Project.allProjects.foreach { p =>
      WS.url(BaseGitHubURL + RepoAPI + p.githubRepo + PullRequestCommand + AccessTokenParam).get().map { response =>
        import model.PullRequestReader._

        val pullRequests = (response.json).validate[Seq[PullRequest]]
        val prs = pullRequests.recover {
          case a =>
            println(a)
            Nil
        }.get
        
        channel.push(views.xml.project(p, prs).body)
      }
    }
  }


}