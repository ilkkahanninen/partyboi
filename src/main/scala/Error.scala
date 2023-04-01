package org.jumalauta.partyboi

trait AppError {
  def errorType: String
  def message: String
  def id: Option[String]
}

case class InternalServerErrorResponse(
  errorType: String = "INTERNAL_ERROR",
  message: String = "Internal server error",
  id: Option[String] = None,
) extends AppError