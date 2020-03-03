Pod::Spec.new do |s|
  s.name         = "RNMergeImages"
  s.version      = "0.1.0"
  s.summary      = "RNMergeImages"
  s.description  = <<-DESC
                  RNMergeImages
                   DESC
  s.homepage     = "https://github.com/csae0/react-native-merge-images"
  s.license      = "MIT"
  s.author             = { "author" => "contact@mvpstars.com" }
  s.platform     = :ios, "7.0"
  s.source       = { :git => "https://github.com/csae0/react-native-merge-images.git", :branch => "imageCollage" }
  s.source_files  = "ios/**/*.{h,m,swift}"
  s.requires_arc = true
  s.dependency "React"
  #s.dependency "others"
end
