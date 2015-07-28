
import java.nio.file.Files;
import java.util.regex.Pattern

class Line implements Comparable<Line> {

	double dateTime
	def value


	@Override
	public int compareTo(Line that) {
		if(this&&!that){
			return 1
		}else if(that&&!this){
			return -1;
		}else{
			return dateTime - that.dateTime;
		}
	}
}


def fins = [new File('raw.html'), new File('raw_2.html')]

def fout = new File('out.html')

if(fout.exists()){
	Files.copy(fout.toPath(), new File(fout.absolutePath+"_"+System.nanoTime()).toPath())
	fout.delete()
}

def bout = new BufferedWriter(new FileWriter(fout));

def namesKnown = [] as Set
def priority = [] as List
def finalQueue = []
int linesOut = 0
int linesIn = 0
int skippedLines = 0
Line last = null

def fileBreak = """<div class='break'>End Of File</div>"""
def firstFile = true;

def timeStamp = 0d

def parserTimeStamp = new XmlParser()

StringBuilder block = null;

fins.each{  def fin ->

	if(firstFile){
		firstFile = false
	}else
	if(last){
		if( block.size()>0){
			block.append("</div>")

			last = new Line(value:block.toString(), dateTime: timeStamp)

			priority.add last
			linesOut++
			block = new StringBuilder();
		}else{
			println "Skipping empty block"
		}

		priority.add  new Line(value:fileBreak, dateTime: timeStamp+0.01d)
	}


	int skipNext = 0
	boolean removeNextA = false
	def lineQueue = []

	fin.eachLine {
		linesIn++
		String line = it;

		if(skipNext<=0){
			def splits = line.split("<")
			boolean first = true;

			splits.each{
				if(!first){
					lineQueue << "<"+it
				}else{
					lineQueue << it
					first = false
				}
			}
		}else{
			skipNext--;
		}
	}

	def SCRIPT = 'script'
	def skipUntil = null

	skipNext = 0
	removeNextA = false


	def hitBody = false
	boolean firstMessage = true



	def skipToNextName = false
	lineQueue.each {
		boolean writeln = true;

		String line = it;
		if(skipUntil){
		}else  if(!hitBody){
			if(line.startsWith("<body")){
				hitBody = true
				writeln = false
			}else{
				writeln = false
			}
		}else if(skipNext>0){
			skipNext--
			writeln = false
		}else if(line.startsWith('<')&&(!line.startsWith('<p'))&&!line.startsWith('<a ')&&!line.startsWith('<abbr')&&!line.startsWith('<img')){
			writeln = false
		}

		boolean newMessage = false

		if(writeln){
			if(line.startsWith('<a ')){
				if(line.contains('hidden_elem')||line.contains('"mrs _')||line.contains('uiCloseButton')||line.contains('uiBoxLightblue')){
					writeln = false
				}else if(line.contains('data-hovercard')){
					def name = line.substring(line.indexOf(">")+1)
					def nameClass = "person${name.hashCode()}"
					if(!name){
						skipToNextName = true;
					}else{
						if(!namesKnown.contains(name)){
							namesKnown<<name
						}

						line = "<div class='message $nameClass'><div class='name'>"+name+"</div>"

						if(timeStamp)
							line = line+ "<!--$timeStamp-->"

						if(firstMessage){
							firstMessage=false;
						}
						newMessage = true;
					}
				}else if(firstMessage){
					writeln = false
				}else {
					writeln = false;
					//line = line+"</a><br>"
				}
			}else if(line.startsWith('<abbr')){
				line = line+"</abbr>"

				def searchText = 'data-utime="'
				def timeStampTxt = line.substring(line.indexOf(searchText)+searchText.length())
				timeStampTxt = timeStampTxt.substring(0, timeStampTxt.indexOf('"'))

				def timeStampNew = new Double(timeStampTxt)

				if(timeStampNew){
					timeStamp = timeStampNew
				}else{
					writeln = false
				}

				if(firstMessage){
					writeln = false
				}
			}else if(line.startsWith('<img')){
				if(firstMessage){
					writeln = false
				}else if(!line.contains('_50dv')&&!line.contains('_9g')&&!line.contains('width="1"')&&!line.contains('hidden_elem')){
					line = line+"</img>"
				}else{
					writeln = false
				}
			}
		}


		if(skipToNextName){
			if(newMessage){
				skipToNextName = false
			}else{
				writeln = false
			}
		}

		if(newMessage && block){
			if( block.size()>0){
				//block.append("<p>timeStamp : $timeStamp</p>")
				block.append("</div>")

				last = new Line(value:block.toString(), dateTime: timeStamp)

				priority.add last
				linesOut++
				block = new StringBuilder();
			}else{
				println "Skipping empty block"
			}
		}

		if(writeln){
			if(block==null){
				block = new StringBuilder();
			}
			if(timeStamp>0){
				block.append(line)
			}else{
				println line
				skippedLines++
			}
		}
	}
}
priority.sort()

def dupedRemoved = 0
def timesSeen = [] as Set
priority.each{

	if(!timesSeen.contains(it.dateTime)){
		finalQueue << it.value
		timesSeen << it.dateTime
	}else{
		println "Removed Dupe ${it.value}"
		dupedRemoved ++;
	}
}

def colors = ['lightblue', 'antiquewhite']
def index = 0;
bout << """ <html>
<head>
<style>
abbr {
  color: grey;
}
.message {
  margin-bottom: 10px;
}
p {
    margin-bottom: 1px;
    -webkit-margin-after: 1px;
    -webkit-margin-before: 1px;
    padding-bottom: 1px;
}
.name {
  font-weight: bold;
  background-color: rgba(0,0,0,0.1);
}
"""
namesKnown.each {
	bout << ".person${it.hashCode()}{  background-color:${colors[index]} }\r\n"
	index++
	index%=colors.size()
}

bout << """</style>
</head>
<body>
<div class='header'>ConvoParser</div>
"""

finalQueue.each{
	if(it)
		bout << it + "\r\n"
}

bout<<"""
</div>
<div class='footer'>End Of Data</div>
</body>
</html>"""

bout.flush()
bout.close()

println "Lines In $linesIn, lines out $linesOut, skipped $skippedLines, dupes $dupedRemoved"
