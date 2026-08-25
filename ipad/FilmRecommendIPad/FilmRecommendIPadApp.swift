import SwiftUI

@main
struct FilmRecommendIPadApp: App {
    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}

struct ContentView: View {
    @StateObject private var viewModel = MovieViewModel()

    var body: some View {
        IPadBrowseView(viewModel: viewModel)
            .task { viewModel.loadDataset() }
            .preferredColorScheme(.dark)
    }
}
