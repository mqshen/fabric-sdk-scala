package org.hyperledger.fabric.sdk

import org.hyperledger.fabric.sdk.ca.{Enrollment, EnrollmentRequest, MemberServicesFabricCAImpl}

/**
  * Created by goldratio on 17/02/2017.
  */
class User(name: String, chain: Chain) {
  var enrollment: Option[Enrollment] = None

  def isEnrolled = {
    this.enrollment.isDefined
  }

  def enroll(enrollmentSecret: String) = {
    val req = EnrollmentRequest(name, enrollmentSecret)

    this.enrollment = Some(MemberServicesFabricCAImpl.instance.enroll(req))
    //this.saveState()
    this.enrollment
  }

}
