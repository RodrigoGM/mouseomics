import java.io.File
import scala.util.Random

import org.broadinstitute.gatk.queue.QScript
import org.broadinstitute.gatk.queue.extensions.gatk._
import org.broadinstitute.gatk.queue.function.ListWriterFunction

class IndelRealignmet extends QScript {
  qscript =>

  /****************************************************************************
  * Required Parameters
  *****************************************************************************/

  @Input(doc="The reference file for the bam files.", shortName="R", fullName="reference_sequence")
  var referenceFile: File = _ // _ is scala shorthand for null
  
  @Input(doc="Bam file to relalign", shortName="I", fullName="input_file")
  var bamFiles: Seq[File] = Nil

  /****************************************************************************
   * Optional Parameters
   *****************************************************************************/

  @Argument(doc="scatter parameter", shortName="P", fullName="scatter_parameter", required=false)
  var scatter:  Int = _  
  
  @Argument(doc="nt parameter", shortName="N", fullName="num_threads", required=false)
  var nt:  Int = _
  
  @Argument(doc="nct parameter", shortName="C", fullName="num_cpu_threads_per_data_thread", required=false)
  var nct:  Int = _
  
  @Input(doc="Intervals to realign", shortName="L", required = false)
  var intervals: File = _

  @Input(doc="dbSNP file", shortName="D", fullName="dbsnp", required=false)
  var dbsnp: File = _

  @Input(doc="Known in/del VCF file", shortName="known", required=false)
  var known: File = _

  @Input(doc="known polymorphic sites that BQSR skips", shortName="knownSites", fullName = "knownSites", required=false)
  var knownSites: Seq[File] = Nil

  @Input(doc="BQSR recalibration table", shortName="BQSR", required=false)
  var BQSR: File = _

  @Input(doc="First recalibration table", shortName="before", required=false)
  var before: File = _

  @Input(doc="Second recalibration table; BQSR with first recalibration table", shortName="after", required=false)
  var after: File = _

  @Output(doc = "CSV recalibration table", shortName = "csv", required = false)
  var csv: File = _

  @Output(doc = "PDF of BQSR plots", shortName = "plots", required = false)
  var plots: File = _


  /****************************************************************************
   * CommonArguments
   *****************************************************************************/
  
  trait CommonArguments extends CommandLineGATK {
    this.reference_sequence = qscript.referenceFile
    this.intervals = if (qscript.intervals == null) Nil else List(qscript.intervals)
    this.memoryLimit = 12
  }
  
  
  /****************************************************************************               
   * Main script
   *****************************************************************************/
  
  def script() {
    
      val targetCreator = new RealignerTargetCreator with CommonArguments
      val indelRealigner = new IndelRealigner with CommonArguments
      val bqsr = new BaseRecalibrator with CommonArguments
      val bqsr2 = new BaseRecalibrator with CommonArguments
      val analyzeCovariates = new AnalyzeCovariate with CommonArguments
      val applyRecalibration = new PrintReads with CommonArguments

      targetCreator.input_file +:= qscript.myBam
      targetCreator.nt = qscript.nt
      targetCreator.known +:= qscript.known
      targetCreator.out = swapExt(myBam, "bam", "intervals")

      indelRealigner.input_file +:= qscript.myBam
      indelRealigner.targetIntervals = targetCreator.out
      indelRealigner.known +:= qscript.known
      indelRealigner.out = swapExt(qscript.myBam, "dd.bam", "dd.ir.bam")

      bqsr.input_file +:= indelRealigner.out
      bqsr.knownSites +:= Seq(qscript.knownSites)
      bqsr.out = swapExt(indelRealigner.out, "dd.ir.bam", "bqrecal")
      targetCreator.nct = qscript.nct

      bqsr.input_file +:= indelRealigner.out
      bqsr.knownSites +:= Seq(qscript.knownSites)
      bqsr.out = swapExt(indelRealigner.out, "dd.ir.bam", "bqrecal")
      bqsr.nct = qscript.nct

      bqsr2.input_file +:= indelRealigner.out
      bqsr2.knownSites +:= Seq(qscript.knownSites)
      bqsr2.BQSR +:= bqsr.out
      bqsr2.out = swapExt(bqsr.out, "dd.ir.bam", "bqrecal2")
      bqsr2.nct = qscript.nct

      analyzeCovariates.before = bqsr.out
      analyzeCovariates.after = bqsr2.out
      analyzeCovariates.csv = swapExt(myBam, "dd.bam", "recal.csv")
      analyzeCovariates.plots = swapExt(myBam, "dd.bam", "bqsr.pdf")

      applyRecalibration.input_file +:= indelRealigner.out
      applyRecalibration.BQSR +:= bqsr2.out
      applyRecalibration.out = swapExt(indelRealigner.out, "dd.ir.bam", "dd.ir.bqsr.bam")

      add(targetCreator, indelRealigner, bqsr, bqsr2, analyzeCovariates, applyRecalibration)
      
  }
  
}	
