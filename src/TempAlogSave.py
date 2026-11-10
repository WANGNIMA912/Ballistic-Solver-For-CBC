import math
velocity = 2
dragCoef = 0.99
gravityCoef = 0.05
tick=70.48922874064803

#new horizontal shot alogrithm
HorizontalShotDist = velocity * dragCoef * (1 + dragCoef)*(dragCoef**tick - 1)/(2*dragCoef - 2)
TestHorizontalShot=100

reverseTick=math.log(2*TestHorizontalShot*(dragCoef-1)/(velocity*dragCoef*(1+dragCoef))+1,dragCoef)
print("HorizontalShotDist", HorizontalShotDist)
print("reverseTick", reverseTick)

#new vertical shot alogrithm
tick=2
VerticalVeloTermStart=velocity*dragCoef**tick-gravityCoef*(dragCoef**tick-1)/(dragCoef-1)
tickEnd=tick+1
VerticalVeloTermEnd=velocity*dragCoef**(tickEnd)-gravityCoef*(dragCoef**(tickEnd)-1)/(dragCoef-1)

dummy=gravityCoef*(dragCoef**tick-1)/(dragCoef-1)
VerticalDistTerm=dragCoef**tick*(velocity*(dragCoef+1)-gravityCoef)/2-dummy


print("VerticalVeloTermStart", VerticalVeloTermStart)
print("VerticalVeloTermEnd", VerticalVeloTermEnd)
print("VerticalDistTerm", VerticalDistTerm)

#le vertical dist cal
componentU0=velocity*(dragCoef+1)-gravityCoef
componentU1=dragCoef*(dragCoef**tick-1)
componentD0=dragCoef-1
VerticalDist=(componentU0*componentU1/2-gravityCoef*(componentU1/componentD0-tick))/componentD0
print("VerticalDist", VerticalDist)
