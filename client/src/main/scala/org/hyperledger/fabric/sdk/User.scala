package org.hyperledger.fabric.sdk

import org.hyperledger.fabric.sdk.ca.{ Enrollment, EnrollmentRequest, MemberServicesFabricCAImpl }

/**
 * Created by goldratio on 17/02/2017.
 */
class User(name: String) {
  var enrollment: Option[Enrollment] = None

  def isEnrolled = {
    this.enrollment.isDefined
  }

}
