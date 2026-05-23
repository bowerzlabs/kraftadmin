package logging

import security.AdminUserDTO

class NoOpKraftAuditor : KraftAdminAuditor {
    override fun record(action: KraftLogAction, resource: String, id: String, actor: AdminUserDTO) {
        println("no logs set for this application")
    }
}