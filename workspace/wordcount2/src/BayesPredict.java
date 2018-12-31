import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;


public class BayesPredict
{
		// 初始化全局参数
		public static void InitGlobalPara(){
			NBMain.preciseCount = 0;	// 归零
		}
		
		// 训练, 得到统计频数文件
		public static void GetFrequencyFile(String[] otherArgs) throws Exception{
			Job trainJob = new Job(NBMain.conf, "word count2");	// 新建一个 Job，传入配置信息	JobTrack 和 TaskTrack 都是 MRv1 用的东西了

			trainJob.setJarByClass(BayesClassifier.class);		// 设置主类
			trainJob.setMapperClass(BayesClassifier.TokenizerMapper.class);	// 设置 Mapper 类
			trainJob.setCombinerClass(BayesClassifier.IntSumReducer.class);	// 设置作业合成类	(就是在 map 之后、reduce 之前、要进行一次)
			trainJob.setReducerClass(BayesClassifier.IntSumReducer.class);	// 设置 Reducer 类
			trainJob.setOutputKeyClass(Text.class);			// 设置输出数据的关键类
			trainJob.setOutputValueClass(IntWritable.class);	// 设置输出值类
			
			// Train model, 就是统计每一个 特征属性 好评、差评 的频率
			FileInputFormat.addInputPath(trainJob, new Path(otherArgs[0]));	// 文件输入
			FileOutputFormat.setOutputPath(trainJob, new Path(otherArgs[1]));// 文件输出
			
			boolean flag = trainJob.waitForCompletion(true);
			System.out.print("SUCCEED!" + flag);
		}
		
		// 预测文件
		public static void Predict() throws Exception{
			// countFrequencyJob: 判断输入文件中每一行的值
			Job predictJob = new Job(NBMain.conf, "countFrequencyJob");
			predictJob.setJarByClass(BayesClassifier.class);		// 设置主类
			predictJob.setMapperClass(BayesClassifier.BayesPredictMapper.class);	// 设置 Mapper 类
			predictJob.setNumReduceTasks(0);   	 			//reduce的数量设为0
			
			FileInputFormat.addInputPath(predictJob, new Path("hdfs://192.168.10.100:9000/user/Hadoop/BayesData/test-1000.txt"));	// 文件输入
			FileOutputFormat.setOutputPath(predictJob, new Path("hdfs://192.168.10.100:9000/user/Hadoop/eclipseOutput4"));// 文件输出
			boolean flag = predictJob.waitForCompletion(true);
			System.out.println("GetFrequencyMap SUCCEED!" + flag);
			
			// 删除输出的文件夹、没有找到不产生输出文件的方法...
//				FileSystem fileSystem = FileSystem.get(new URI("hdfs://192.168.10.100:9000"), conf);
//				if (fileSystem.exists(new Path("/user/Hadoop/eclipseOutput4"))) {
//					fileSystem.delete(new Path("/user/Hadoop/eclipseOutput4"), true);
//					System.out.println("delete output file SUCCEED!");
//				}
		}
}
