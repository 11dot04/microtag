package com.microtag.inspect

/**
 * Per 100 g of the food as it is usually eaten (cooked where it is cooked). Values are rounded typical figures
 * from standard food composition tables (USDA, India's IFCT) and product labels, so every card calls them approximate.
 *
 * name | aliases | kcal | protein g | carbs g | fat g | fibre g | serving g | serving unit | cup g (0 = unknown)
 */
internal const val FOOD_DATA = """
roti|chapati,phulka,chapatti,rotis,chapatis|297|9.6|46|7.5|11|40|piece|0
paratha|parantha,plain paratha,parathas|326|7|45|13|4|80|piece|0
naan|naan bread,nan|290|9|50|5.5|2.2|90|piece|0
puri|poori,puris|380|8|45|18|3|30|piece|0
dosa|dosai,plain dosa,dosas|168|3.9|29|3.7|1|85|piece|0
idli|idly,idlis|148|4.2|30|0.4|1.5|40|piece|0
poha|flattened rice|130|2.6|25|2.2|1.5|180|katori|0
upma|rava upma|130|3|20|4|2|180|katori|0
rice|white rice,cooked rice,steamed rice,chawal|130|2.7|28.2|0.3|0.4|150|katori|158
brown rice|cooked brown rice|123|2.7|25.6|1|1.8|150|katori|195
biryani|chicken biryani,veg biryani|180|8.5|22|6.5|1|250|serving|0
khichdi|khichri|110|4|18|2.5|2|200|katori|0
dal|daal,dhal,lentils,toor dal,moong dal,masoor dal|110|6.5|15|3|3|150|katori|200
rajma|kidney beans,rajma curry|127|8.7|22.8|0.5|6.4|150|katori|177
chana|chickpeas,chole,chhole,chana masala|164|8.9|27.4|2.6|7.6|150|katori|164
paneer|cottage cheese|296|18|3|22|0|100|serving|0
curd|yogurt,yoghurt,dahi,plain yogurt|61|3.5|4.7|3.3|0|100|katori|245
greek yogurt|greek yoghurt|97|9|3.9|5|0|170|cup|245
buttermilk|chaas,chhaas,mattha|40|3.3|4.8|0.9|0|245|glass|245
milk|full cream milk,whole milk,cow milk|61|3.2|4.8|3.3|0|244|glass|244
skimmed milk|skim milk,toned milk,low fat milk|34|3.4|5|0.1|0|245|glass|245
cheese|cheddar,cheddar cheese,cheese slice|403|24.9|1.3|33.1|0|20|slice|0
butter|makhan|717|0.9|0.1|81|0|5|tsp|0
ghee|clarified butter|900|0|0|100|0|5|tsp|0
oil|cooking oil,vegetable oil,olive oil,sunflower oil|884|0|0|100|0|14|tbsp|0
egg|eggs,boiled egg,boiled eggs,anda|155|12.6|1.1|10.6|0|50|egg|0
egg white|egg whites|52|10.9|0.7|0.2|0|33|egg|0
omelette|omelet,omelettes|154|10.6|0.6|11.7|0|60|piece|0
chicken|chicken breast,grilled chicken,cooked chicken|165|31|0|3.6|0|100|serving|0
chicken curry|chicken gravy|150|13|4|9|1|200|katori|0
mutton|lamb,mutton curry|294|25|0|21|0|100|serving|0
fish|cooked fish,fish fry|130|22|0|4|0|100|serving|0
salmon|grilled salmon|208|20|0|13|0|100|serving|0
tuna|canned tuna|116|25.5|0|0.8|0|100|serving|0
prawns|shrimp|99|24|0.2|0.3|0|100|serving|0
bacon|bacon strips|541|37|1.4|42|0|8|slice|0
tofu|bean curd|76|8|1.9|4.8|0.3|100|serving|0
soya chunks|soy chunks,nutrela,soya|345|52|33|0.5|13|30|serving|0
whey protein|protein powder,whey|380|78|8|6|0|30|scoop|0
mixed vegetables|sabzi,veg curry,vegetable curry,mixed veg|90|2|9|5|2.5|150|katori|0
potato|potatoes,boiled potato,aloo|87|1.9|20.1|0.1|1.8|150|medium|0
sweet potato|shakarkandi|90|2|20.7|0.2|3.3|130|medium|0
french fries|fries,chips and fries|312|3.4|41|15|3.8|117|serving|0
samosa|samosas|262|3.5|24|17|2|60|piece|0
onion|onions|40|1.1|9.3|0.1|1.7|110|medium|0
tomato|tomatoes|18|0.9|3.9|0.2|1.2|123|medium|0
cucumber|kheera,cucumbers|15|0.7|3.6|0.1|0.5|200|medium|0
carrot|carrots,gajar|41|0.9|9.6|0.2|2.8|61|medium|0
spinach|palak|23|2.9|3.6|0.4|2.2|30|cup|30
cauliflower|gobi,gobhi|25|1.9|5|0.3|2|100|serving|0
broccoli|broccolis|34|2.8|6.6|0.4|2.6|91|cup|91
cabbage|patta gobhi|25|1.3|5.8|0.1|2.5|89|cup|89
peas|matar,green peas|81|5.4|14.5|0.4|5.7|145|cup|145
corn|sweet corn,bhutta|86|3.3|19|1.4|2.7|100|serving|0
mushroom|mushrooms|22|3.1|3.3|0.3|1|70|cup|70
okra|bhindi,lady finger|33|1.9|7.5|0.2|3.2|100|serving|0
brinjal|eggplant,baingan,aubergine|25|1|5.9|0.2|3|100|serving|0
capsicum|bell pepper,bell peppers,shimla mirch|20|0.9|4.6|0.2|1.7|120|medium|0
beetroot|beet,beets|43|1.6|9.6|0.2|2.8|100|serving|0
avocado|avocados|160|2|8.5|14.7|6.7|136|medium|0
banana|bananas,kela|89|1.1|22.8|0.3|2.6|118|medium|0
apple|apples,seb|52|0.3|13.8|0.2|2.4|182|medium|0
orange|oranges,santra|47|0.9|11.8|0.1|2.4|131|medium|0
mango|mangoes,mangos,aam|60|0.8|15|0.4|1.6|165|cup|165
grapes|grape,angoor|69|0.7|18.1|0.2|0.9|151|cup|151
watermelon|tarbooz|30|0.6|7.6|0.2|0.4|152|cup|152
papaya|papita|43|0.5|10.8|0.3|1.7|140|cup|140
pomegranate|anar|83|1.7|18.7|1.2|4|174|cup|174
guava|amrud|68|2.6|14.3|1|5.4|100|medium|0
pineapple|ananas|50|0.5|13.1|0.1|1.4|165|cup|165
strawberries|strawberry|32|0.7|7.7|0.3|2|152|cup|152
coconut|fresh coconut|354|3.3|15.2|33.5|9|45|piece|0
dates|khajoor,khajur|282|2.5|75|0.4|8|24|piece|0
almonds|almond,badam|579|21.2|21.6|49.9|12.5|28|handful|0
cashews|cashew,kaju|553|18.2|30.2|43.9|3.3|28|handful|0
peanuts|peanut,moongfali,groundnuts|567|25.8|16.1|49.2|8.5|28|handful|0
walnuts|walnut,akhrot|654|15.2|13.7|65.2|6.7|28|handful|0
peanut butter||588|25|20|50|6|16|tbsp|0
bread|white bread,bread slice,bread slices|265|9|49|3.2|2.7|27|slice|0
brown bread|whole wheat bread,wholemeal bread|247|13|41|3.4|7|28|slice|0
oats|rolled oats,dry oats|389|16.9|66.3|6.9|10.6|40|serving|80
oatmeal|porridge,cooked oats|71|2.5|12|1.5|1.7|234|cup|234
pasta|spaghetti,macaroni,penne,noodles|158|5.8|30.9|0.9|1.8|140|cup|140
cornflakes|cereal,corn flakes|357|7.5|84|0.4|3.3|30|serving|0
pizza|pizza slice|266|11|33|10|2.3|107|slice|0
burger|hamburger,veg burger|250|12|30|9|1.5|110|piece|0
sugar|white sugar,cheeni|387|0|100|0|0|4|tsp|0
honey|shahad|304|0.3|82.4|0|0.2|21|tbsp|0
jaggery|gur,gud|383|0.4|98|0.1|0|10|piece|0
dark chocolate|dark choc|546|4.9|61|31|7|10|piece|0
chocolate|milk chocolate,choco|535|7.7|59.4|29.7|3.4|40|piece|0
ice cream|icecream|207|3.5|23.6|11|0.7|66|serving|132
gulab jamun|gulab jamuns|320|4|50|11|0.5|40|piece|0
jalebi|jalebis|410|3|65|20|0.3|20|piece|0
rasgulla|rasgullas,rosogolla|186|5|35|2|0|50|piece|0
laddu|ladoo,laddoo,besan laddu|450|8|55|22|2|40|piece|0
biscuit|biscuits,cookie,cookies|450|6.5|72|15|2|10|piece|0
chips|potato chips,crisps,wafers|536|7|53|34|4.4|28|serving|0
popcorn|pop corn|387|12.9|77.8|4.5|14.5|8|cup|8
chai|milk tea,masala chai,tea with milk|50|1.5|7|1.8|0|150|cup|150
coffee|black coffee|1|0.1|0|0|0|240|cup|240
cappuccino|latte,milk coffee|42|2.7|3.5|2|0|240|cup|240
cola|coke,pepsi,soda,soft drink,cold drink|42|0|10.6|0|0|330|can|240
orange juice|fruit juice|45|0.7|10.4|0.2|0.2|248|glass|248
coconut water|nariyal pani|19|0.7|3.7|0.2|1.1|240|glass|240
lassi|sweet lassi|90|2.5|14|2.5|0|250|glass|245
beer|lager|43|0.5|3.6|0|0|330|can|240
wine|red wine,white wine|85|0.1|2.6|0|0|150|glass|240
"""
