import hudson.model.*;
import jenkins.model.*;

println "--> disabling controller executors"
Jenkins.instance.setNumExecutors(0)
