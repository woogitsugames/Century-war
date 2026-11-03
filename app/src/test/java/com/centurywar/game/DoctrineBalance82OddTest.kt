package com.centurywar.game

import org.junit.Test

/** Lustrzany test doktryn z nieparzystych pozycji [Doctrine.entries]; opis: [DoctrineBalance82Base]. */
class DoctrineBalance82OddTest : DoctrineBalance82Base() {
    @Test fun allDoctrinesStayBetweenFortyAndSixtyFivePercent() = checkDoctrines(1)
}
