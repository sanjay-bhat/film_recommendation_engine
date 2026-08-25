import SwiftUI

struct ContentView: View {
    @StateObject private var viewModel = MovieViewModel()

    var body: some View {
        TVBrowseView(viewModel: viewModel)
            .task { viewModel.loadDataset() }
            .preferredColorScheme(.dark)
    }
}
