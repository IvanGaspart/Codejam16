# Use Scenarios for larp

## Splash page (where you land, transitions into Main View)
- monochrome colour with Figlet text saying "larp" in one of the following figlet fonts:
    - ANSI Shadow
    - Colossal
    - Ghost
    - Bloody
    - Isometric1
    - Sub-Zero
    - Epic
    - Roman
    - Script
    - Poison
    - Delta Corps Priest 1
    - Block
    - 3D-ASCII
    - Graffiti
- the figlet text should drop in from the top of the screen, each letter dropping staggeredly (couple of ms delta) with some gravity effect to end up near the centre of the screen (not necessarily perfectly aligned)
- one of the following loading messages in italics underneath (randomly selected):
    - on your larps, get set...
    - larpity larp larp...
    - larping up a storm...
    - initiating L.A.R.P. protocol...
    - mastering the art of the larp...
    - larp me up, Scotty...
    - who you gon' call...
    - larping into hyperspace...
    - larping like nobody's watching...
- load in for 1 to 2ish seconds, then fade/dissolve into Main View with a sick animation
    - maybe particle effects of emojis randomised from the following:
        - book
        - bird
        - leaf
        - troll
        - mask
        - crying
        - wilted rose
        - peace fingers
        - scream
        - sunglasses
        - smirk
        - pinocchio
        - sleep
        - yawn
        - zany face
- have a cookie or smth so the splash page only loads once per same tab
    - cookie or smth deleted when tab closed or after certain delay


## Main View (split)
- Interactive map on top with building select
    - each possible building is labeled with:
        - its name
        - its average occupancy (qualitative label i.e. "QUIET" (< 30%), "MODERATE" (30–59%), "BUSY" (60–84%), "FULL" (≥ 85%), and "NO_DATA" (when covered is false))
    - building can be selected by pressing it on the map
        - when pressed:
            - it becomes highlighted (see landing_page.svg)
            - the floors section on the bottom becomes updated to show its floors
    - map can be moved around like any map api
        - no annoying map UI buttons to crowd it up though
    - default should be unselected
        - minimise Floor view as much as possible initially until building is selected or search is started
- Floor view on the bottom
    - ordered with most free (lowest capacity) on top
    - coloration should reflect occupancy (same general vibe as landing_page.svg, although not necessarily same colour scheme)
    - NO_DATA should be different (i.e. grey) and sink to the bottom
    - on an occupancy tie, lowest floors float up
- Search bar in the middle
    - search for:
        - occupancy label
        - building name
    - filter by:
        - occupancy threshold
- Dark mode icon in the top right to toggle light/dark mode


## Floor View
- Transition here from Main View when a floor from the search results (map and/or search bar) is clicked from the bottom half of screen
    - should be an animated expansion of bottom half to fill full screen (see and compare landing_page with floor_view.svg)
- button in top left corner allows animated return to landing_page
    - use reverse of fade-in expansion animation
- dark mode icon still in top right
- Floors should now be sorted from lowest (bottom) to highest (top) in terms of floor number
- current floor should occupy most space (see floor_view.svg) and display metrics like those in the reference SVG
    - you may resize the words for the metrics based on your design principles, but the types and overall layout should be in the same spirit
- looking at the floor directly above or below can be done by clicking, and should incur a scrolling animation
    - you can also scroll up and down continuously
        - when the scrolling stops, though, the viewed floor should end up centred and re-focussed like the reference SVG
            - it should not be an expansion animation here, though, all floors during scroll should appear the same height: it's just that the non-focussed directly-adjacent ones appear squished because it is just a tiny label that is visible when they are not directly in focus
        - if you scroll all the way to the top floor, scrolling more should trigger an animation that naturally brings the user back to the Main View, just like the top right button does


## General
- If possible (do not do if deemed to complex, KISS principle), reduce the animations if latency is high/packets are dropping/whatever readily-available ground truth on connection quality is available is indicating a bad connection
- KISS
- Make animation SUPER buttery smooth
- aesthetic should emulate the SVGs for structure but be very sleek and minimalist
- mono font, all things should be lowercase except maybe occupancy labels for clarity
