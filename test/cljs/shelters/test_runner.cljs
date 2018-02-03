(ns shelters.test-runner
  (:require
   [doo.runner :refer-macros [doo-tests]]
   [shelters.core-test]
   [shelters.common-test]))

(enable-console-print!)

(doo-tests 'shelters.core-test
           'shelters.common-test)
