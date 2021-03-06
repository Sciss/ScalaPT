/*
 * Experiment1.scala
 * (Rays)
 *
 * Copyright (c) 2016 Hanns Holger Rutz. All rights reserved.
 *
 * This software is published under the GNU Lesser General Public License v2.1+
 *
 * Image output licensed under
 * Creative Commons Attribution-NonCommercial-NoDerivs 3.0 Unported License
 * (CC BY-NC-ND 3.0)
 *
 * https://creativecommons.org/licenses/by-nc-nd/3.0/
 *
 * For further information, please contact Hanns Holger Rutz at
 * contact@sciss.de
 */

package de.sciss.rays

import java.awt.image.BufferedImage
import java.awt.{Color, Insets}
import javax.imageio.ImageIO

import de.sciss.file._
import de.sciss.numbers

object Experiment1 {
  def main(args: Array[String]): Unit = {
    run()
  }

  import numbers.Implicits._

  case class FadeIn(numFrames: Int, amt: Double = 1.0, start: Int = 0) {
    def apply(frame: Int): Double = (frame - start).linlin(0.0, numFrames, 0.0, amt).clip(0.0, amt)
  }

  case class ExpFadeIn(numFrames: Int, amt: Double = 1.0, start: Int = 0, floor: Double = 1.0e-4) {
    private[this] final val amt1 = amt + floor
    def apply(frame: Int): Double = ((frame - start).linexp(0.0, numFrames, floor, amt1) - floor).clip(0.0, amt)
  }

  case class EasyInEasyOut(numFrames: Int, from: Double, to: Double, start: Int = 0) {
    def apply(frame: Int): Double = {
      (frame - start).clip(0, numFrames).linlin(0.0, numFrames, math.Pi/2, 0).cos.squared.linlin(0, 1, from, to)
    }
  }

  case class Move(from: Point3, to: Point3, numFrames: Int, start: Int = 0) {
    def apply(frame: Int): Point3 = {
      val w = (frame - start).linlin(0.0, numFrames, 0.0, 1.0).clip(0.0, 1.0)
      val x = w.linlin(0, 1, from.x, to.x)
      val y = w.linlin(0, 1, from.y, to.y)
      val z = w.linlin(0, 1, from.z, to.z)
      Point3(x, y, z)
    }
  }

  val fps = 25

  implicit class TimeOps(private val d: Double) extends AnyVal {
    /** Converts seconds to frames. */
    def seconds: Int = (d * fps).toInt
  }

  val fdInRed     = ExpFadeIn( 5.0.seconds, 0.997)
  val fdInGreen   = ExpFadeIn( 7.5.seconds, 0.998)
  val fdInBlue    = ExpFadeIn(10.0.seconds, 0.999)

  val fdOutRed    = EasyInEasyOut(from = 0.997, to = 0.0, start = 45.0.seconds, numFrames = 15.0.seconds)
  val fdOutGreen  = EasyInEasyOut(from = 0.998, to = 0.0, start = 47.5.seconds, numFrames = 12.5.seconds)
  val fdOutBlue   = EasyInEasyOut(from = 0.999, to = 0.0, start = 50.0.seconds, numFrames = 10.0.seconds)

  val glassPos    = Move(Point3(27, 16.5, 47), Point3(73, 16.5, 78), 60.0.seconds)
  val mirrorPos   = Move(Point3(73, 16.5, 78), Point3(27, 16.5, 47), 60.0.seconds)

  def objects(frame: Int): List[Shape] = {
    val red   = fdInRed  (frame) min fdOutRed  (frame)
    val green = fdInGreen(frame) min fdOutGreen(frame)
    val blue  = fdInBlue (frame) min fdOutBlue (frame)

    val matRef  = Material.reflective(red, green, blue)

    List(
      Plane("left"  , matRef, Axis.X, posFacing = true ,   1),
      Plane("right" , matRef, Axis.X, posFacing = false,  99),
      Plane("back"  , matRef, Axis.Z, posFacing = true ,   0),
      Plane("front" , Material.diffuse(RGB.black), Axis.Z, posFacing = false, 170),
      Plane("bottom", matRef, Axis.Y, posFacing = true ,   0),
      Plane("top"   , matRef, Axis.Y, posFacing = false,  81.6),

      Sphere("mirror", Material.reflective(RGB.white * 0.999), mirrorPos(frame) /* Point3(27, 16.5, 47) */, 16.5),
      Sphere("glass" , Material.refractive(RGB.white * 0.999), glassPos (frame) /* Point3(73, 16.5, 78) */, 16.5),

      Sphere("light" , Material.emissive(RGB.white * 12), Point3(50, 681.6 - 0.27, 81.6), 600.0)
    )
  }

  def scene(frame: Int) = Scene(
    Camera(
      Ray(
        Point3(50, 52 * 0.7, 295.6 * 0.7),
        Vector3(0, -0.042612 * 2.0, -1)
      ), 0.5135
    ), objects(frame))

  val outDir = file("image_out")
  if (!outDir.exists()) outDir.mkdirs()

  val w     = 1920
  val h     = 1080
  val scale = 0.5
  val image = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
  val win   = new ImageFrame(title0 = "Exp", image0 = image, scale0 = scale)
  win.open()

  val renderData  = new Array[Array[SuperSampling]](h)
  val gr2d        = image.getGraphics
  gr2d.setColor(Color.RED)
  gr2d.drawRect(0, 0, w-1, h-1)
  win.repaint()

  val numIter     = 8
  val numFrames   = 60.0.seconds

  def run(): Unit = {
    val r = new Rendering(image)
    for (fr <- 0 until numFrames) {
      println(s"${new java.util.Date()} : ----- frame $fr -----")
      val f = outDir / s"frame-$fr.png"
      if (!f.exists()) {
        val rdr = new MonteCarloRenderer(w, h, scene(fr))
        for (i <- 0 until numIter) {
          if (!win.closed) {
            r.iterate(i, rdr)
            win.repaint()
          }
        }
        saveImage(f)
      }
    }
  }

  def saveImage(file: File): Unit = {
    val name = file.getName
    val dotPos = name.lastIndexOf('.')
    val format =
      if (dotPos != -1) {
        name.substring(dotPos + 1)
      } else {
        "png"
      }
    println(s"Saving '$name'")
    if (!ImageIO.write(image, format, file)) {
      System.out.println("ERROR: filename prefix '" + format + " not recognised as a format")
    }
  }

  val ins: Insets = win.viewer.insets
}