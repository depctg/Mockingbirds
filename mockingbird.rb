require 'nokogiri'

TEMPFILE = ARGV[1]
TARGETFILE = ARGV[2]
MODULELNAME = ARGV[0]

tempDoc = Nokogiri::XML(open(TEMPFILE))
targetDoc = Nokogiri::XML(open(TARGETFILE))

pinTrait = {}
appearTemp = tempDoc.at_css "circuit[name=#{MODULELNAME}] appear"
tempDoc.css("circuit[name=#{MODULELNAME}] [name=Pin]").each do |pin|
  pinTrait[pin['loc'][1...-1]] = pin.at_css('[name=label]')['val']
end

pinTrait.each do |k,v|
  appearTemp.at_css("[pin='#{k}']")['label'] = v
end

pinTrait.clear
targetDoc.css("circuit[name=#{MODULELNAME}] [name=Pin]").each do |pin|
  pinTrait[pin.at_css('[name=label]')['val']] = pin['loc'][1...-1]
end

pinTrait.each do |k,v|
  appearTemp.at_css("[label='#{k}']")['pin'] = v
end

targetDoc.css("circuit[name=#{MODULELNAME}]>a").last.add_next_sibling(appearTemp)

File.open("mocked_#{TARGETFILE}", "w") do |f|
    f.write(targetDoc.to_xml)
end
